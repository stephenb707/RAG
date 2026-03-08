package com.rag.backend.agent.workflow;

import com.rag.backend.agent.dto.AgentApplyProposalRequest;
import com.rag.backend.agent.dto.AgentApplyProposalResponse;
import com.rag.backend.agent.dto.ApplyPatchRequest;
import com.rag.backend.agent.dto.ApplyPatchResponse;
import com.rag.backend.agent.fs.PatchConflictException;
import com.rag.backend.agent.fs.RepoFsService;
import com.rag.backend.agent.fs.RepoPatchService;
import com.rag.backend.agent.exec.CommandRunnerService;
import com.rag.backend.agent.proposal.AgentProposalRecord;
import com.rag.backend.agent.proposal.AgentProposalStoreService;
import com.rag.backend.agent.run.AgentRunStoreService;
import com.rag.backend.agent.verification.AgentVerificationService;
import com.rag.backend.agent.verification.VerificationRunResult;
import com.rag.backend.agent.verification.VerificationStageResult;
import com.rag.backend.agent.verification.VerificationStatus;
import com.rag.backend.agent.blastradius.BlastRadiusService;
import com.rag.backend.agent.blastradius.BlastRadiusApprovalRequiredException;
import com.rag.backend.agent.blastradius.BlastRadiusAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentProposalWorkflowServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private AgentChangeWorkflowService changeWorkflowService;
    @Mock
    private AgentProposalStoreService proposalStore;
    @Mock
    private AgentRunStoreService runStore;
    @Mock
    private RepoFsService repoFsService;
    @Mock
    private RepoPatchService repoPatchService;
    @Mock
    private CommandRunnerService commandRunnerService;
    @Mock
    private AgentVerificationService verificationService;
    @Mock
    private BlastRadiusService blastRadiusService;

    private AgentProposalWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new AgentProposalWorkflowService(
                changeWorkflowService,
                proposalStore,
                runStore,
                repoFsService,
                repoPatchService,
                verificationService,
                blastRadiusService
        );
    }

    @Test
    void apply_uses_stored_expected_shas_and_calls_repo_patch_service() {
        String proposalId = "prop-1";
        List<ApplyPatchRequest.PatchChange> patchChanges = List.of(
                new ApplyPatchRequest.PatchChange("src/Main.java", "expectedSha123", "newContent")
        );
        AgentProposalRecord proposal = new AgentProposalRecord(
                proposalId,
                "test-repo",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(new AgentProposalRecord.ProposedEditEntry("src/Main.java", "r", "newContent")),
                patchChanges,
                List.of(),
                false,
                "run-1",
                null,
                null,
                "summary",
                List.of(),
                false,
                null
        );
        when(proposalStore.get(proposalId)).thenReturn(proposal);
        when(repoFsService.resolveRepoRoot("test-repo")).thenReturn(tempDir);
        when(repoPatchService.apply(eq(tempDir), any(ApplyPatchRequest.class)))
                .thenReturn(new ApplyPatchResponse("test-repo", 1, List.of(
                        new ApplyPatchResponse.FileResult("src/Main.java", false, "expectedSha123", "afterSha", 100L)
                )));

        AgentApplyProposalResponse response = workflowService.apply(
                new AgentApplyProposalRequest(proposalId, false, null, null, null, null)
        );

        ArgumentCaptor<ApplyPatchRequest> captor = ArgumentCaptor.forClass(ApplyPatchRequest.class);
        verify(repoPatchService).apply(eq(tempDir), captor.capture());
        ApplyPatchRequest used = captor.getValue();
        assertThat(used.repoName()).isEqualTo("test-repo");
        assertThat(used.changes()).hasSize(1);
        assertThat(used.changes().get(0).path()).isEqualTo("src/Main.java");
        assertThat(used.changes().get(0).expectedSha256()).isEqualTo("expectedSha123");
        assertThat(used.changes().get(0).newContent()).isEqualTo("newContent");

        assertThat(response.proposalId()).isEqualTo(proposalId);
        assertThat(response.status()).isEqualTo("applied");
        assertThat(response.applyResult().appliedCount()).isEqualTo(1);
        verify(proposalStore).updateStatus(proposalId, "applied");
    }

    @Test
    void apply_fails_cleanly_when_sha_changed_since_proposal() {
        String proposalId = "prop-2";
        AgentProposalRecord proposal = new AgentProposalRecord(
                proposalId,
                "test-repo",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(),
                List.of(new ApplyPatchRequest.PatchChange("src/Main.java", "oldSha", "newContent")),
                List.of(),
                false,
                null,
                null,
                null,
                "summary",
                List.of(),
                false,
                null
        );
        when(proposalStore.get(proposalId)).thenReturn(proposal);
        when(repoFsService.resolveRepoRoot("test-repo")).thenReturn(tempDir);
        when(repoPatchService.apply(eq(tempDir), any(ApplyPatchRequest.class)))
                .thenThrow(new PatchConflictException("expectedSha256 mismatch for src/Main.java. expected=oldSha actual=otherSha"));

        assertThatThrownBy(() -> workflowService.apply(new AgentApplyProposalRequest(proposalId, false, null, null, null, null)))
                .isInstanceOf(PatchConflictException.class)
                .hasMessageContaining("expectedSha256 mismatch");

        verify(proposalStore, never()).updateStatus(any(), any());
    }

    @Test
    void apply_throws_when_proposal_not_found() {
        when(proposalStore.get("missing")).thenReturn(null);

        assertThatThrownBy(() -> workflowService.apply(new AgentApplyProposalRequest("missing", false, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void apply_throws_when_proposal_already_applied() {
        AgentProposalRecord proposal = new AgentProposalRecord(
                "prop-3",
                "r", ".", "g", "2025-01-01T00:00:00Z", "applied",
                "p", List.of(), List.of(), List.of(), false, null, null, null, "s", List.of(), false, null
        );
        when(proposalStore.get("prop-3")).thenReturn(proposal);

        assertThatThrownBy(() -> workflowService.apply(new AgentApplyProposalRequest("prop-3", false, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status=applied");
    }

    @Test
    void apply_with_verification_all_stages_pass_returns_verification_result_no_follow_up() {
        String proposalId = "prop-verify-ok";
        AgentProposalRecord proposal = new AgentProposalRecord(
                proposalId,
                "test-repo",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(new AgentProposalRecord.ProposedEditEntry("src/Main.java", "r", "content")),
                List.of(new ApplyPatchRequest.PatchChange("src/Main.java", "sha", "content")),
                List.of(),
                false,
                "run-1",
                null,
                null,
                "summary",
                List.of(),
                false,
                null
        );
        when(proposalStore.get(proposalId)).thenReturn(proposal);
        when(repoFsService.resolveRepoRoot("test-repo")).thenReturn(tempDir);
        when(repoPatchService.apply(eq(tempDir), any(ApplyPatchRequest.class)))
                .thenReturn(new ApplyPatchResponse("test-repo", 1, List.of(
                        new ApplyPatchResponse.FileResult("src/Main.java", false, "sha", "after", 100L)
                )));
        VerificationRunResult passedResult = new VerificationRunResult(
                VerificationStatus.passed,
                List.of(new VerificationStageResult("test", List.of("./mvnw", "test"), 0, 100L, "ok", "", false, VerificationStatus.passed)),
                null,
                null
        );
        when(verificationService.runStages(eq("test-repo"), eq("."), any(), eq(true))).thenReturn(passedResult);

        AgentApplyProposalResponse response = workflowService.apply(
                new AgentApplyProposalRequest(proposalId, true, null, null, null, null)
        );

        assertThat(response.verificationResult()).isNotNull();
        assertThat(response.verificationResult().overallStatus()).isEqualTo(VerificationStatus.passed);
        assertThat(response.followUpProposalId()).isNull();
        assertThat(response.failureSummary()).isNull();
        verify(verificationService).runStages(eq("test-repo"), eq("."), any(), eq(true));
    }

    @Test
    void apply_with_verification_failure_creates_follow_up_proposal_and_does_not_apply_it() throws Exception {
        String proposalId = "prop-verify-fail";
        AgentProposalRecord proposal = new AgentProposalRecord(
                proposalId,
                "test-repo",
                ".",
                "Fix the bug",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(new AgentProposalRecord.ProposedEditEntry("src/Main.java", "r", "content")),
                List.of(new ApplyPatchRequest.PatchChange("src/Main.java", "sha", "content")),
                List.of(),
                false,
                "run-1",
                null,
                null,
                "summary",
                List.of(),
                false,
                null
        );
        when(proposalStore.get(proposalId)).thenReturn(proposal);
        when(repoFsService.resolveRepoRoot("test-repo")).thenReturn(tempDir);
        when(repoPatchService.apply(eq(tempDir), any(ApplyPatchRequest.class)))
                .thenReturn(new ApplyPatchResponse("test-repo", 1, List.of(
                        new ApplyPatchResponse.FileResult("src/Main.java", false, "sha", "after", 100L)
                )));
        VerificationRunResult failedResult = new VerificationRunResult(
                VerificationStatus.failed,
                List.of(new VerificationStageResult("test", List.of("./mvnw", "test"), 1, 50L, "out", "FAILED", false, VerificationStatus.failed)),
                "test",
                "Stage 'test' failed (exitCode=1). stderr: FAILED"
        );
        when(verificationService.runStages(eq("test-repo"), eq("."), any(), eq(true))).thenReturn(failedResult);

        String followUpId = "follow-up-123";
        when(proposalStore.generateProposalId()).thenReturn(followUpId);
        when(runStore.createRun(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("run-follow-up");
        when(blastRadiusService.analyze(any(), any())).thenReturn(new BlastRadiusAnalysis(1, 0, 0, 1, List.of(), false));
        when(changeWorkflowService.proposeOnly(any())).thenReturn(new ProposalGenerationResult(
                "fix plan",
                List.of(new com.rag.backend.agent.dto.AgentChangeWorkflowResponse.ProposedEdit("src/Main.java", "fix", "newContent")),
                List.of(new ApplyPatchRequest.PatchChange("src/Main.java", "after", "newContent")),
                List.of()
        ));
        when(proposalStore.get(followUpId)).thenReturn(new AgentProposalRecord(
                followUpId, "test-repo", ".", "goal", "2025-01-01T00:00:00Z", "proposed",
                "plan", List.of(), List.of(), List.of(), false, "run-2", null, null, "s", List.of(), false, null
        ));

        AgentApplyProposalResponse response = workflowService.apply(
                new AgentApplyProposalRequest(proposalId, true, null, null, null, null)
        );

        assertThat(response.verificationResult().overallStatus()).isEqualTo(VerificationStatus.failed);
        assertThat(response.followUpProposalId()).isEqualTo(followUpId);
        assertThat(response.failureSummary()).contains("Stage 'test' failed");
        ArgumentCaptor<AgentProposalRecord> saveCaptor = ArgumentCaptor.forClass(AgentProposalRecord.class);
        verify(proposalStore, atLeast(1)).save(saveCaptor.capture());
        AgentProposalRecord savedWithParent = saveCaptor.getAllValues().stream()
                .filter(r -> proposalId.equals(r.parentProposalId()))
                .findFirst()
                .orElse(null);
        assertThat(savedWithParent).isNotNull();
        assertThat(savedWithParent.verificationFailureStage()).isEqualTo("test");
    }

    @Test
    void apply_with_no_verification_does_not_call_verification_service() {
        String proposalId = "prop-no-verify";
        AgentProposalRecord proposal = new AgentProposalRecord(
                proposalId,
                "test-repo",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(),
                List.of(new ApplyPatchRequest.PatchChange("src/Main.java", "sha", "c")),
                List.of(),
                false,
                null,
                null,
                null,
                "s",
                List.of(),
                false,
                null
        );
        when(proposalStore.get(proposalId)).thenReturn(proposal);
        when(repoFsService.resolveRepoRoot("test-repo")).thenReturn(tempDir);
        when(repoPatchService.apply(eq(tempDir), any(ApplyPatchRequest.class)))
                .thenReturn(new ApplyPatchResponse("test-repo", 1, List.of()));

        workflowService.apply(new AgentApplyProposalRequest(proposalId, false, false, null, null, null));

        verify(verificationService, never()).runStages(any(), any(), any(), anyBoolean());
    }

    @Test
    void apply_rejects_risky_proposal_when_explicitApproval_false() {
        String proposalId = "prop-risky";
        BlastRadiusAnalysis blastRadius = new BlastRadiusAnalysis(4, 0, 0, 4, List.of("many_files"), true);
        AgentProposalRecord proposal = new AgentProposalRecord(
                proposalId,
                "test-repo",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(
                        new AgentProposalRecord.ProposedEditEntry("a.java", "r", "c1"),
                        new AgentProposalRecord.ProposedEditEntry("b.java", "r", "c2"),
                        new AgentProposalRecord.ProposedEditEntry("c.java", "r", "c3"),
                        new AgentProposalRecord.ProposedEditEntry("d.java", "r", "c4")
                ),
                List.of(
                        new ApplyPatchRequest.PatchChange("a.java", "s1", "c1"),
                        new ApplyPatchRequest.PatchChange("b.java", "s2", "c2"),
                        new ApplyPatchRequest.PatchChange("c.java", "s3", "c3"),
                        new ApplyPatchRequest.PatchChange("d.java", "s4", "c4")
                ),
                List.of(),
                false,
                null,
                null,
                null,
                "summary",
                List.of("many_files"),
                true,
                blastRadius
        );
        when(proposalStore.get(proposalId)).thenReturn(proposal);

        assertThatThrownBy(() -> workflowService.apply(new AgentApplyProposalRequest(proposalId, false, null, null, null, null)))
                .isInstanceOf(BlastRadiusApprovalRequiredException.class)
                .hasMessageContaining("explicit approval")
                .satisfies(ex -> {
                    BlastRadiusApprovalRequiredException e = (BlastRadiusApprovalRequiredException) ex;
                    assertThat(e.getProposalId()).isEqualTo(proposalId);
                    assertThat(e.getBlastRadiusReasons()).contains("many_files");
                });
        verify(repoPatchService, never()).apply(any(), any());
    }

    @Test
    void apply_succeeds_for_risky_proposal_when_explicitApproval_true() {
        String proposalId = "prop-risky-approved";
        BlastRadiusAnalysis blastRadius = new BlastRadiusAnalysis(4, 0, 0, 4, List.of("many_files"), true);
        AgentProposalRecord proposal = new AgentProposalRecord(
                proposalId,
                "test-repo",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(
                        new AgentProposalRecord.ProposedEditEntry("a.java", "r", "c1"),
                        new AgentProposalRecord.ProposedEditEntry("b.java", "r", "c2")
                ),
                List.of(
                        new ApplyPatchRequest.PatchChange("a.java", "s1", "c1"),
                        new ApplyPatchRequest.PatchChange("b.java", "s2", "c2")
                ),
                List.of(),
                false,
                null,
                null,
                null,
                "summary",
                List.of("many_files"),
                true,
                blastRadius
        );
        when(proposalStore.get(proposalId)).thenReturn(proposal);
        when(repoFsService.resolveRepoRoot("test-repo")).thenReturn(tempDir);
        when(repoPatchService.apply(eq(tempDir), any(ApplyPatchRequest.class)))
                .thenReturn(new ApplyPatchResponse("test-repo", 2, List.of(
                        new ApplyPatchResponse.FileResult("a.java", false, "s1", "a1", 10L),
                        new ApplyPatchResponse.FileResult("b.java", false, "s2", "a2", 10L)
                )));

        AgentApplyProposalResponse response = workflowService.apply(
                new AgentApplyProposalRequest(proposalId, false, null, null, null, true)
        );

        assertThat(response.status()).isEqualTo("applied");
        assertThat(response.applyResult().appliedCount()).isEqualTo(2);
        verify(repoPatchService).apply(eq(tempDir), any(ApplyPatchRequest.class));
    }
}
