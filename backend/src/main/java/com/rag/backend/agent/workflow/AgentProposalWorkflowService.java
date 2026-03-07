package com.rag.backend.agent.workflow;

import com.rag.backend.agent.dto.*;
import com.rag.backend.agent.exec.CommandRunnerService;
import com.rag.backend.agent.fs.RepoFsService;
import com.rag.backend.agent.fs.RepoPatchService;
import com.rag.backend.agent.proposal.AgentProposalRecord;
import com.rag.backend.agent.proposal.AgentProposalStoreService;
import com.rag.backend.agent.proposal.ProposalRiskFlags;
import com.rag.backend.agent.run.AgentRunStoreService;
import com.rag.backend.agent.verification.AgentVerificationService;
import com.rag.backend.agent.verification.VerificationRunResult;
import com.rag.backend.agent.verification.VerificationStageRequest;
import com.rag.backend.agent.verification.VerificationStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentProposalWorkflowService {

    private static final List<String> DEFAULT_TEST_COMMAND = List.of("./mvnw", "-B", "-ntp", "test");

    private final AgentChangeWorkflowService changeWorkflowService;
    private final AgentProposalStoreService proposalStore;
    private final AgentRunStoreService runStore;
    private final RepoFsService repoFsService;
    private final RepoPatchService repoPatchService;
    private final AgentVerificationService verificationService;

    public AgentProposalWorkflowService(
            AgentChangeWorkflowService changeWorkflowService,
            AgentProposalStoreService proposalStore,
            AgentRunStoreService runStore,
            RepoFsService repoFsService,
            RepoPatchService repoPatchService,
            AgentVerificationService verificationService
    ) {
        this.changeWorkflowService = changeWorkflowService;
        this.proposalStore = proposalStore;
        this.runStore = runStore;
        this.repoFsService = repoFsService;
        this.repoPatchService = repoPatchService;
        this.verificationService = verificationService;
    }

    public AgentProposalResponse propose(AgentProposeWorkflowRequest req) throws Exception {
        String repoName = require(req.repoName(), "repoName");
        String workingDir = (req.workingDir() == null || req.workingDir().isBlank()) ? "." : req.workingDir();
        String goal = require(req.goal(), "goal");
        boolean allowCreate = Boolean.TRUE.equals(req.allowCreate());

        ProposalGenerationResult result = changeWorkflowService.proposeOnly(req);

        String proposalId = proposalStore.generateProposalId();
        String runId = runStore.createRun(repoName, workingDir, goal, "workflow_propose", "gpt-4o", "v1");

        List<String> paths = result.proposedEdits().stream().map(AgentChangeWorkflowResponse.ProposedEdit::path).toList();
        List<String> riskFlags = ProposalRiskFlags.computeRiskFlags(paths);
        boolean requiresApproval = ProposalRiskFlags.requiresApproval(riskFlags);

        List<AgentProposalRecord.ProposedEditEntry> proposedEditEntries = result.proposedEdits().stream()
                .map(e -> new AgentProposalRecord.ProposedEditEntry(e.path(), e.rationale(), e.newContent()))
                .collect(Collectors.toList());

        String summary = buildProposalSummary(goal, result);

        AgentProposalRecord record = new AgentProposalRecord(
                proposalId,
                repoName,
                workingDir,
                goal,
                Instant.now().toString(),
                "proposed",
                result.plan(),
                proposedEditEntries,
                result.patchChanges(),
                result.diffSummaries(),
                allowCreate,
                runId,
                null,
                null,
                summary,
                riskFlags,
                requiresApproval
        );
        proposalStore.save(record);

        List<AgentProposalResponse.ProposedEditSummary> editSummaries = result.proposedEdits().stream()
                .map(e -> new AgentProposalResponse.ProposedEditSummary(e.path(), e.rationale(), e.newContent()))
                .collect(Collectors.toList());

        return new AgentProposalResponse(
                proposalId,
                repoName,
                workingDir,
                goal,
                result.plan(),
                editSummaries,
                result.diffSummaries(),
                riskFlags,
                requiresApproval,
                summary,
                runId,
                "proposed"
        );
    }

    public AgentApplyProposalResponse apply(AgentApplyProposalRequest req) {
        String proposalId = require(req.proposalId(), "proposalId");
        boolean runTests = Boolean.TRUE.equals(req.runTests());
        boolean runVerification = Boolean.TRUE.equals(req.runVerification());
        List<VerificationStageRequest> verificationStages = req.verificationStages() != null ? req.verificationStages() : List.of();

        AgentProposalRecord proposal = proposalStore.get(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Proposal not found: " + proposalId);
        }
        if (!"proposed".equals(proposal.status())) {
            throw new IllegalStateException("Proposal cannot be applied; status=" + proposal.status());
        }

        Path repoRoot = repoFsService.resolveRepoRoot(proposal.repoName());
        String workingDir = (proposal.workingDir() == null || proposal.workingDir().isBlank()) ? "." : proposal.workingDir();
        ApplyPatchRequest patchReq = new ApplyPatchRequest(
                proposal.repoName(),
                proposal.allowCreate(),
                proposal.patchChanges()
        );

        ApplyPatchResponse applyResult = repoPatchService.apply(repoRoot, patchReq);

        proposalStore.updateStatus(proposalId, "applied");

        VerificationRunResult verificationResult = null;
        CommandRunnerService.RunResult testResult = null;
        String followUpProposalId = null;
        String failureSummary = null;
        String finalSummary = buildApplySummary(proposal, applyResult, null, null);

        boolean runVerificationPipeline = runVerification || runTests || !verificationStages.isEmpty();
        if (runVerificationPipeline) {
            List<VerificationStageRequest> stages = !verificationStages.isEmpty()
                    ? verificationStages
                    : List.of(new VerificationStageRequest("test",
                    (req.testCommand() != null && !req.testCommand().isEmpty()) ? req.testCommand() : DEFAULT_TEST_COMMAND));
            verificationResult = verificationService.runStages(proposal.repoName(), workingDir, stages, true);

            if (verificationResult.stages() != null && verificationResult.stages().size() == 1
                    && "test".equals(verificationResult.stages().get(0).name())) {
                var single = verificationResult.stages().get(0);
                testResult = new CommandRunnerService.RunResult(
                        workingDir,
                        single.exitCode(),
                        single.durationMs(),
                        single.stdoutSummary(),
                        single.stderrSummary(),
                        single.truncated()
                );
            }

            finalSummary = buildApplySummary(proposal, applyResult, testResult, verificationResult);

            if (verificationResult.overallStatus() == VerificationStatus.failed || verificationResult.overallStatus() == VerificationStatus.error) {
                failureSummary = verificationResult.failureSummary();
                try {
                    followUpProposalId = createFollowUpProposal(proposal, applyResult, verificationResult);
                } catch (Exception e) {
                    failureSummary = (failureSummary != null ? failureSummary + "\n" : "") + "Follow-up proposal creation failed: " + e.getMessage();
                }
                if (followUpProposalId != null) {
                    finalSummary = finalSummary + "\nFollow-up proposal created: " + followUpProposalId;
                }
            }
        }

        String runId = proposal.originatingRunId();
        if (runId != null) {
            String runSummary = finalSummary;
            if (verificationResult != null && failureSummary != null) {
                runSummary = runSummary + "\nVerification failure: " + truncate(failureSummary, 500);
            }
            runStore.finishRun(runId, "applied", truncate(runSummary, 2000));
        }

        return new AgentApplyProposalResponse(
                proposalId,
                applyResult,
                testResult,
                verificationResult,
                followUpProposalId,
                failureSummary,
                finalSummary,
                runId,
                "applied"
        );
    }

    private String createFollowUpProposal(AgentProposalRecord appliedProposal, ApplyPatchResponse applyResult, VerificationRunResult verificationResult) throws Exception {
        String failedStage = verificationResult.failedStageName() != null ? verificationResult.failedStageName() : "verification";
        String failureLogs = truncate(verificationResult.failureSummary() != null ? verificationResult.failureSummary() : "", 1500);
        List<String> editedPaths = appliedProposal.proposedEdits().stream().map(AgentProposalRecord.ProposedEditEntry::path).toList();
        String goal = buildFollowUpGoal(appliedProposal.goal(), appliedProposal.proposalId(), failedStage, failureLogs, editedPaths);

        AgentProposeWorkflowRequest followUpReq = new AgentProposeWorkflowRequest(
                appliedProposal.repoName(),
                appliedProposal.workingDir(),
                goal,
                editedPaths.isEmpty() ? null : editedPaths,
                null,
                appliedProposal.allowCreate()
        );
        AgentProposalResponse followUpResponse = propose(followUpReq);
        String newProposalId = followUpResponse.proposalId();

        AgentProposalRecord created = proposalStore.get(newProposalId);
        if (created != null) {
            AgentProposalRecord updated = new AgentProposalRecord(
                    created.proposalId(),
                    created.repoName(),
                    created.workingDir(),
                    created.goal(),
                    created.createdAt(),
                    created.status(),
                    created.plan(),
                    created.proposedEdits(),
                    created.patchChanges(),
                    created.diffSummaries(),
                    created.allowCreate(),
                    created.originatingRunId(),
                    appliedProposal.proposalId(),
                    failedStage,
                    created.summary(),
                    created.riskFlags(),
                    created.requiresApproval()
            );
            proposalStore.save(updated);
        }
        return newProposalId;
    }

    private static String buildFollowUpGoal(String originalGoal, String appliedProposalId, String failedStage, String failureLogs, List<String> editedPaths) {
        StringBuilder sb = new StringBuilder();
        sb.append("Original goal: ").append(originalGoal).append("\n\n");
        sb.append("A previous proposal (").append(appliedProposalId).append(") was applied but verification failed.\n");
        sb.append("Failed stage: ").append(failedStage).append("\n\n");
        sb.append("Failure output:\n").append(failureLogs).append("\n\n");
        sb.append("Request: Propose a minimal follow-up fix so that the verification stage '").append(failedStage).append("' passes. Only change what is necessary.");
        if (!editedPaths.isEmpty()) {
            sb.append(" Prefer editing these files that were changed: ").append(String.join(", ", editedPaths));
        }
        sb.append(".");
        return sb.toString();
    }

    private static String buildProposalSummary(String goal, ProposalGenerationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: ").append(goal).append("\n");
        sb.append("Plan: ").append(result.plan()).append("\n");
        sb.append("Proposed edits: ").append(result.proposedEdits().size()).append(" file(s).\n");
        int added = result.diffSummaries().stream().mapToInt(AgentProposalResponse.DiffSummaryItem::addedLines).sum();
        int removed = result.diffSummaries().stream().mapToInt(AgentProposalResponse.DiffSummaryItem::removedLines).sum();
        sb.append("Diff totals: +").append(added).append(" / -").append(removed).append("\n");
        return sb.toString();
    }

    private static String buildApplySummary(AgentProposalRecord proposal, ApplyPatchResponse applyResult,
                                           CommandRunnerService.RunResult testResult,
                                           VerificationRunResult verificationResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("Proposal applied: ").append(proposal.proposalId()).append("\n");
        sb.append("Files written: ").append(applyResult.appliedCount()).append("\n");
        if (verificationResult != null) {
            sb.append("Verification: ").append(verificationResult.overallStatus().name()).append("\n");
            if (verificationResult.stages() != null) {
                for (var s : verificationResult.stages()) {
                    sb.append("  ").append(s.name()).append(": ").append(s.status()).append(" (exitCode=").append(s.exitCode()).append(")\n");
                }
            }
        }
        if (testResult != null) {
            sb.append("Test exit code: ").append(testResult.exitCode()).append("\n");
            sb.append(testResult.exitCode() == 0 ? "Tests passed." : "Tests failed.").append("\n");
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static String require(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " is required");
        return v;
    }
}
