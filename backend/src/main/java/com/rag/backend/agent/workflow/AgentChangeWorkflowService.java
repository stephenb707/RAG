package com.rag.backend.agent.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.backend.agent.dto.*;
import com.rag.backend.agent.exec.CommandRunnerService;
import com.rag.backend.agent.fs.RepoFsService;
import com.rag.backend.agent.fs.RepoPatchService;
import com.rag.backend.agent.util.JsonExtraction;
import com.rag.backend.ai.OpenAIChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentChangeWorkflowService {

    private static final int MAX_PROPOSED_FILES = 3;
    private static final int MAX_PROPOSED_FILES_LEGACY = 8;

    private final RepoFsService repoFsService;
    private final RepoPatchService repoPatchService;
    private final CommandRunnerService commandRunnerService;
    private final OpenAIChatClient llm;
    private final DiffSummaryService diffSummaryService;
    private final ObjectMapper objectMapper;

    public AgentChangeWorkflowService(
            RepoFsService repoFsService,
            RepoPatchService repoPatchService,
            CommandRunnerService commandRunnerService,
            OpenAIChatClient llm,
            DiffSummaryService diffSummaryService,
            ObjectMapper objectMapper
    ) {
        this.repoFsService = repoFsService;
        this.repoPatchService = repoPatchService;
        this.commandRunnerService = commandRunnerService;
        this.llm = llm;
        this.diffSummaryService = diffSummaryService;
        this.objectMapper = objectMapper;
    }

    public AgentChangeWorkflowResponse run(AgentChangeWorkflowRequest req) throws IOException {
        String repoName = require(req.repoName(), "repoName");
        String workingDir = (req.workingDir() == null || req.workingDir().isBlank()) ? "." : req.workingDir();
        String goal = require(req.goal(), "goal");

        List<String> filePaths = (req.filePaths() == null) ? List.of() : req.filePaths().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(MAX_PROPOSED_FILES_LEGACY)
                .toList();

        Path repoRoot = repoFsService.resolveRepoRoot(repoName);
        Map<String, RepoFsService.ReadFileData> beforeFiles = new LinkedHashMap<>();
        for (String p : filePaths) {
            beforeFiles.put(p, repoFsService.readFile(repoRoot, p));
        }

        List<AgentChangeWorkflowResponse.ProposedEdit> proposed = proposeEditsAndPlan(goal, beforeFiles).edits();

        ApplyPatchRequest applyReq = toApplyPatchRequest(repoName, proposed, beforeFiles);
        ApplyPatchResponse applyResp = repoPatchService.apply(repoRoot, applyReq);

        List<String> testCmd = (req.testCommand() == null || req.testCommand().isEmpty())
                ? List.of("./mvnw", "-B", "-ntp", "test")
                : req.testCommand();

        CommandRunnerService.RunResult testRun = commandRunnerService.run(repoName, workingDir, testCmd);

        Map<String, String> afterShaByPath = new HashMap<>();
        if (applyResp.results() != null) {
            for (ApplyPatchResponse.FileResult w : applyResp.results()) {
                afterShaByPath.put(w.path(), w.afterSha256());
            }
        }

        List<AgentChangeWorkflowResponse.FileDiffSummary> diffs = new ArrayList<>();
        for (AgentChangeWorkflowResponse.ProposedEdit e : proposed) {
            String path = e.path();
            RepoFsService.ReadFileData before = beforeFiles.get(path);
            if (before == null) continue;

            String afterSha = afterShaByPath.get(path);
            String afterContent = e.newContent();

            DiffSummaryService.DiffSummary d = diffSummaryService.summarize(
                    path,
                    String.join("\n", before.lines()),
                    before.sha256(),
                    afterContent,
                    afterSha == null ? sha256Hex(afterContent) : afterSha
            );

            diffs.add(new AgentChangeWorkflowResponse.FileDiffSummary(
                    d.path(),
                    d.beforeSha256(),
                    d.afterSha256(),
                    d.addedLines(),
                    d.removedLines(),
                    d.diffSnippet()
            ));
        }

        String summary = buildSummary(goal, proposed, applyResp, testRun, diffs);

        return new AgentChangeWorkflowResponse(
                repoName,
                workingDir,
                goal,
                proposed,
                applyResp,
                testRun,
                diffs,
                summary
        );
    }

    private record ProposeResult(String plan, List<AgentChangeWorkflowResponse.ProposedEdit> edits) {}

    private ProposeResult proposeEditsAndPlan(String goal, Map<String, RepoFsService.ReadFileData> beforeFiles) throws IOException {
        return proposeEditsAndPlan(goal, beforeFiles, true);
    }

    private ProposeResult proposeEditsAndPlan(
            String goal,
            Map<String, RepoFsService.ReadFileData> beforeFiles,
            boolean allowCreate
    ) throws IOException {
        StringBuilder ctx = new StringBuilder();
        ctx.append("Goal:\n").append(goal).append("\n\n");
        if (!beforeFiles.isEmpty()) {
            ctx.append("Context files (path + full content):\n");
            for (var e : beforeFiles.entrySet()) {
                ctx.append("=== FILE: ").append(e.getKey()).append(" (sha256=").append(e.getValue().sha256()).append(") ===\n");
                ctx.append(String.join("\n", e.getValue().lines())).append("\n\n");
            }
        }
        String system = String.join("\n",
                "You are an expert software engineer.",
                "Propose code edits to accomplish the Goal.",
                "Return STRICT JSON only (no markdown).",
                "Schema:",
                "{ \"plan\": \"short plan (optional)\", \"edits\": [ {\"path\": \"relative/path.ext\", \"rationale\": \"...\", \"newContent\": \"FULL FILE CONTENT\" } ] }",
                "Rules:",
                "- Provide full file contents (not a patch).",
                "- Only modify files that were provided in the context, unless you truly must add a new file.",
                "- Keep changes minimal and safe.",
                "- If no changes are needed, return {\"edits\": []}."
        );
        String raw = llm.chat(system, ctx.toString());
        String cleaned = JsonExtraction.extractJson(raw);
        Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<>() {});
        String plan = asString(parsed.get("plan"));
        if (plan != null) plan = plan.trim();
        if (plan == null || plan.isBlank()) plan = "Proposed edits to accomplish goal.";
        Object editsObj = parsed.get("edits");
        if (!(editsObj instanceof List<?> list)) {
            throw new IllegalArgumentException("LLM did not return expected JSON with 'edits' list.");
        }
        List<AgentChangeWorkflowResponse.ProposedEdit> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?,?> m)) continue;
            String path = asString(m.get("path"));
            String rationale = asString(m.get("rationale"));
            String newContent = asString(m.get("newContent"));
            if (path == null || path.isBlank() || newContent == null) continue;
            out.add(new AgentChangeWorkflowResponse.ProposedEdit(path.trim(), rationale == null ? "" : rationale.trim(), newContent));
        }
        if (out.size() > MAX_PROPOSED_FILES) out = out.subList(0, MAX_PROPOSED_FILES);
        return new ProposeResult(plan, out);
    }

    /** Generates a proposal without writing files. Used by propose-then-apply workflow. */
    public ProposalGenerationResult proposeOnly(AgentProposeWorkflowRequest req) throws IOException {
        String repoName = require(req.repoName(), "repoName");
        String goal = require(req.goal(), "goal");
        boolean allowCreate = Boolean.TRUE.equals(req.allowCreate());
        List<String> seedPaths = (req.seedFilePaths() == null) ? List.of() : req.seedFilePaths().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().limit(MAX_PROPOSED_FILES_LEGACY).toList();
        Path repoRoot = repoFsService.resolveRepoRoot(repoName);
        Map<String, RepoFsService.ReadFileData> beforeFiles = new LinkedHashMap<>();
        for (String p : seedPaths) {
            try {
                beforeFiles.put(p, repoFsService.readFile(repoRoot, p));
            } catch (Exception e) {
                if (!allowCreate) throw e;
            }
        }
        ProposeResult proposeResult = proposeEditsAndPlan(goal, beforeFiles, allowCreate);
        List<AgentChangeWorkflowResponse.ProposedEdit> proposed = proposeResult.edits();
        if (proposed.isEmpty()) {
            return new ProposalGenerationResult(proposeResult.plan(), List.of(), List.of(), List.of());
        }
        ApplyPatchRequest applyReq = toApplyPatchRequest(repoName, proposed, beforeFiles, allowCreate);
        List<AgentProposalResponse.DiffSummaryItem> diffSummaries = new ArrayList<>();
        for (AgentChangeWorkflowResponse.ProposedEdit e : proposed) {
            String path = e.path();
            RepoFsService.ReadFileData before = beforeFiles.get(path);
            String beforeContent = before == null ? "" : String.join("\n", before.lines());
            String beforeSha = before == null ? "" : before.sha256();
            String afterContent = e.newContent();
            String afterSha = sha256Hex(afterContent);
            DiffSummaryService.DiffSummary d = diffSummaryService.summarize(path, beforeContent, beforeSha, afterContent, afterSha);
            diffSummaries.add(new AgentProposalResponse.DiffSummaryItem(
                    d.path(), d.beforeSha256(), d.afterSha256(), d.addedLines(), d.removedLines(), d.diffSnippet()));
        }
        return new ProposalGenerationResult(proposeResult.plan(), proposed, applyReq.changes(), diffSummaries);
    }

    private ApplyPatchRequest toApplyPatchRequest(
            String repoName,
            List<AgentChangeWorkflowResponse.ProposedEdit> proposed,
            Map<String, RepoFsService.ReadFileData> beforeFiles
    ) {
        return toApplyPatchRequest(repoName, proposed, beforeFiles, true);
    }

    private ApplyPatchRequest toApplyPatchRequest(
            String repoName,
            List<AgentChangeWorkflowResponse.ProposedEdit> proposed,
            Map<String, RepoFsService.ReadFileData> beforeFiles,
            boolean allowCreate
    ) {
        List<ApplyPatchRequest.PatchChange> changes = proposed.stream()
                .map(e -> {
                    RepoFsService.ReadFileData before = beforeFiles.get(e.path());
                    String expectedSha = before == null ? null : before.sha256();
                    return new ApplyPatchRequest.PatchChange(e.path(), expectedSha, e.newContent());
                })
                .collect(Collectors.toList());
        return new ApplyPatchRequest(repoName, allowCreate, changes);
    }

    private static String buildSummary(
            String goal,
            List<AgentChangeWorkflowResponse.ProposedEdit> proposed,
            ApplyPatchResponse apply,
            CommandRunnerService.RunResult runResult,
            List<AgentChangeWorkflowResponse.FileDiffSummary> diffs
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: ").append(goal).append("\n");
        sb.append("Edits proposed: ").append(proposed.size()).append("\n");
        sb.append("Files written: ").append(apply.results() == null ? 0 : apply.results().size()).append("\n");

        sb.append("Test exit code: ").append(runResult.exitCode()).append("\n");

        int totalAdded = diffs.stream().mapToInt(AgentChangeWorkflowResponse.FileDiffSummary::addedLines).sum();
        int totalRemoved = diffs.stream().mapToInt(AgentChangeWorkflowResponse.FileDiffSummary::removedLines).sum();
        sb.append("Diff totals: +").append(totalAdded).append(" / -").append(totalRemoved).append("\n");

        if (runResult.exitCode() == 0) {
            sb.append("Result: tests passed.\n");
        } else {
            sb.append("Result: tests failed. See testRun stdout/stderr.\n");
        }
        return sb.toString();
    }

    private static String require(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " is required");
        return v;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String sha256Hex(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
