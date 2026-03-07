package com.rag.backend.agent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.backend.agent.dto.*;
import com.rag.backend.agent.exec.CommandRunnerService;
import com.rag.backend.agent.fs.RepoFsService;
import com.rag.backend.agent.fs.RepoPatchService;
import com.rag.backend.agent.run.AgentRunIterationRecord;
import com.rag.backend.agent.run.AgentRunStoreService;
import com.rag.backend.agent.run.PatchResultRecord;
import com.rag.backend.agent.run.PatchedFileRecord;
import com.rag.backend.agent.run.ToolCallRecord;
import com.rag.backend.agent.run.VerificationResultRecord;
import com.rag.backend.agent.util.JsonExtraction;
import com.rag.backend.ai.OpenAIChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    private static final int MAX_TRANSCRIPT_CHARS = 20_000;
    private static final int MAX_FILES_READ_PER_RUN = 10;
    private static final int MAX_JSON_PARSE_RETRIES = 2;
    private static final int DEFAULT_MAX_ITERATIONS = 6;
    private static final int DEFAULT_MAX_TOOL_CALLS = 25;
    private static final List<String> DEFAULT_TEST_COMMAND = List.of("./mvnw", "-B", "-ntp", "test");
    private static final int TOOL_RESULT_MAX_LENGTH = 2000;
    private static final int VERIFICATION_OUTPUT_MAX_LENGTH = 2000;

    private final RepoFsService repoFsService;
    private final RepoPatchService repoPatchService;
    private final CommandRunnerService commandRunnerService;
    private final OpenAIChatClient llm;
    private final ObjectMapper objectMapper;
    private final AgentRunStoreService runStore;

    public AgentLoopService(
            RepoFsService repoFsService,
            RepoPatchService repoPatchService,
            CommandRunnerService commandRunnerService,
            OpenAIChatClient llm,
            ObjectMapper objectMapper,
            AgentRunStoreService runStore
    ) {
        this.repoFsService = repoFsService;
        this.repoPatchService = repoPatchService;
        this.commandRunnerService = commandRunnerService;
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.runStore = runStore;
    }

    public AgentLoopResponse run(AgentLoopRequest req) throws IOException {
        String repoName = require(req.repoName(), "repoName");
        String workingDir = (req.workingDir() == null || req.workingDir().isBlank()) ? "." : req.workingDir();
        String goal = require(req.goal(), "goal");
        int maxIterations = req.maxIterations() != null && req.maxIterations() > 0 ? req.maxIterations() : DEFAULT_MAX_ITERATIONS;
        int maxToolCalls = req.maxToolCalls() != null && req.maxToolCalls() > 0 ? req.maxToolCalls() : DEFAULT_MAX_TOOL_CALLS;
        boolean allowCreate = Boolean.TRUE.equals(req.allowCreate());

        Path repoRoot = repoFsService.resolveRepoRoot(repoName);
        String runId = runStore.createRun(repoName, workingDir, goal);

        try {
        List<AgentLoopResponse.Iteration> iterations = new ArrayList<>();
        StringBuilder transcript = new StringBuilder();
        int filesReadCount = 0;
        int toolCallsCount = 0;
        String finalSummary = "";
        String status = "max_iterations";

        appendToTranscript(transcript, "Goal: " + goal + "\n");
        appendToTranscript(transcript, "Constraints: Only use allowed tools. File edits require expectedSha256. Commands must pass allowlist.\n");

        List<String> seedPaths = (req.seedFilePaths() == null) ? List.of() : req.seedFilePaths().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().limit(5).toList();
        if (!seedPaths.isEmpty()) {
            appendToTranscript(transcript, "Seed files (initial context): " + String.join(", ", seedPaths) + "\n");
            for (String p : seedPaths) {
                if (filesReadCount >= MAX_FILES_READ_PER_RUN) break;
                try {
                    RepoFsService.ReadFileData data = repoFsService.readFile(repoRoot, p);
                    String content = String.join("\n", data.lines());
                    appendToTranscript(transcript, "--- FILE " + p + " (sha256=" + data.sha256() + ") ---\n" + truncate(content, 3000) + "\n");
                    filesReadCount++;
                } catch (Exception e) {
                    appendToTranscript(transcript, "--- FILE " + p + " read error: " + e.getMessage() + " ---\n");
                }
            }
        }

        for (int i = 0; i < maxIterations; i++) {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = transcript.toString();
            if (userPrompt.length() > MAX_TRANSCRIPT_CHARS) {
                userPrompt = userPrompt.substring(userPrompt.length() - MAX_TRANSCRIPT_CHARS);
            }

            AgentLoopDecision decision = null;
            String rawOutput = null;
            for (int retry = 0; retry <= MAX_JSON_PARSE_RETRIES; retry++) {
                try {
                    rawOutput = llm.chat(systemPrompt, userPrompt);
                    String cleaned = JsonExtraction.extractJson(rawOutput);
                    decision = objectMapper.readValue(cleaned, AgentLoopDecision.class);
                    break;
                } catch (JsonProcessingException e) {
                    if (retry == MAX_JSON_PARSE_RETRIES) {
                        String preview = rawOutput == null ? "" : rawOutput.substring(0, Math.min(500, rawOutput.length()));
                        throw new IllegalArgumentException("Invalid JSON from model after " + (MAX_JSON_PARSE_RETRIES + 1) + " tries. Parse error: " + e.getMessage() + ". First 500 chars of raw output: " + preview);
                    }
                    log.warn("Loop iteration {} JSON parse retry {}", i, retry + 1, e);
                }
            }

            if (decision == null) continue;

            String mode = decision.mode() == null ? "" : decision.mode().trim().toLowerCase(Locale.ROOT);
            List<String> errors = new ArrayList<>();
            String toolCallName = null;
            Object toolCallArgs = null;
            String toolResultSummary = null;
            ApplyPatchResponse appliedPatchResult = null;
            CommandRunnerService.RunResult testRun = null;

            if ("finish".equals(mode)) {
                finalSummary = decision.finalSummary() != null ? decision.finalSummary().trim() : "";
                status = "finished";
                appendToTranscript(transcript, "Decision: finish. Summary: " + truncate(finalSummary, 2000) + "\n");
                iterations.add(new AgentLoopResponse.Iteration(i, decision, null, null, null, null, null, null, errors));
                persistIteration(runId, iterations.get(iterations.size() - 1));
                break;
            }

            if ("tool".equals(mode)) {
                toolCallsCount++;
                if (toolCallsCount > maxToolCalls) {
                    errors.add("max_tool_calls exceeded");
                    status = "max_iterations";
                    iterations.add(new AgentLoopResponse.Iteration(i, decision, decision.tool(), decision.args(), null, null, null, null, errors));
                    persistIteration(runId, iterations.get(iterations.size() - 1));
                    break;
                }
                toolCallName = decision.tool();
                toolCallArgs = decision.args();
                if (!isAllowedTool(toolCallName)) {
                    throw new IllegalArgumentException("unknown tool: " + toolCallName + ". Allowed: repo.search, repo.readFile, repo.listFiles, repo.getTree, repo.applyPatch, exec.runCommand");
                }
                try {
                    ToolResult tr = executeTool(repoName, workingDir, repoRoot, decision, filesReadCount, allowCreate);
                    filesReadCount = tr.filesReadCount;
                    toolResultSummary = tr.summary;
                    appendToTranscript(transcript, "Tool " + toolCallName + " result: " + truncate(tr.summary, 4000) + "\n");
                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    errors.add(msg != null ? msg : ex.getClass().getSimpleName());
                    appendToTranscript(transcript, "Tool " + toolCallName + " error: " + msg + "\n");
                }
                iterations.add(new AgentLoopResponse.Iteration(i, decision, toolCallName, toolCallArgs, toolResultSummary, null, null, null, errors));
                persistIteration(runId, iterations.get(iterations.size() - 1));
                continue;
            }

            if ("edit".equals(mode)) {
                if (decision.edits() == null || decision.edits().isEmpty()) {
                    errors.add("edit mode requires non-empty edits");
                    appendToTranscript(transcript, "Edit skipped: no edits provided.\n");
                } else {
                    try {
                        List<ApplyPatchRequest.PatchChange> changes = new ArrayList<>();
                        for (AgentLoopDecision.EditItem e : decision.edits()) {
                            String path = e.path();
                            if (path == null || path.isBlank()) continue;
                            path = path.trim();
                            String expectedSha = e.expectedSha256();
                            if (expectedSha == null || expectedSha.isBlank()) {
                                try {
                                    RepoFsService.ReadFileData data = repoFsService.readFile(repoRoot, path);
                                    expectedSha = data.sha256();
                                } catch (Exception ignored) {
                                    errors.add("missing expectedSha256 for " + path);
                                    continue;
                                }
                            }
                            String newContent = e.newContent() != null ? e.newContent() : "";
                            changes.add(new ApplyPatchRequest.PatchChange(path, expectedSha.trim(), newContent));
                        }
                        if (!changes.isEmpty()) {
                            ApplyPatchRequest patchReq = new ApplyPatchRequest(repoName, allowCreate, changes);
                            appliedPatchResult = repoPatchService.apply(repoRoot, patchReq);
                            toolResultSummary = "Applied " + appliedPatchResult.appliedCount() + " file(s).";
                            appendToTranscript(transcript, "Patch applied: " + toolResultSummary + " " + (appliedPatchResult.results() != null ? appliedPatchResult.results().stream().map(ApplyPatchResponse.FileResult::path).collect(Collectors.joining(", ")) : "") + "\n");
                        }
                    } catch (Exception ex) {
                        String msg = ex.getMessage();
                        errors.add(msg != null ? msg : ex.getClass().getSimpleName());
                        appendToTranscript(transcript, "Apply patch error: " + msg + "\n");
                    }
                }
                iterations.add(new AgentLoopResponse.Iteration(i, decision, null, null, toolResultSummary, appliedPatchResult, null, null, errors));
                persistIteration(runId, iterations.get(iterations.size() - 1));
                continue;
            }

            if ("verify".equals(mode)) {
                List<String> testCmd = (decision.testCommand() != null && !decision.testCommand().isEmpty())
                        ? decision.testCommand()
                        : (req.testCommand() != null && !req.testCommand().isEmpty() ? req.testCommand() : DEFAULT_TEST_COMMAND);
                try {
                    testRun = commandRunnerService.run(repoName, workingDir, testCmd);
                    toolResultSummary = "exitCode=" + testRun.exitCode() + " durationMs=" + testRun.durationMs() + " stdoutLines=" + (testRun.stdout() != null ? testRun.stdout().split("\n").length : 0);
                    appendToTranscript(transcript, "Verify: " + toolResultSummary + "\n" + truncate(testRun.stdout() + "\n" + testRun.stderr(), 3000) + "\n");
                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    errors.add(msg != null ? msg : ex.getClass().getSimpleName());
                    appendToTranscript(transcript, "Verify error: " + msg + "\n");
                }
                iterations.add(new AgentLoopResponse.Iteration(i, decision, null, null, toolResultSummary, null, testRun, testCmd, errors));
                persistIteration(runId, iterations.get(iterations.size() - 1));
                continue;
            }

            errors.add("unknown mode: " + mode);
            iterations.add(new AgentLoopResponse.Iteration(i, decision, null, null, null, null, null, null, errors));
            persistIteration(runId, iterations.get(iterations.size() - 1));
        }

        runStore.finishRun(runId, status, finalSummary);
        return new AgentLoopResponse(runId, repoName, workingDir, goal, iterations, finalSummary, status);
        } catch (Exception e) {
            String msg = e.getMessage();
            runStore.finishRun(runId, "error", msg != null ? truncate(msg, 500) : e.getClass().getSimpleName());
            if (e instanceof IOException io) throw io;
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    private void persistIteration(String runId, AgentLoopResponse.Iteration it) {
        String decisionJson = null;
        try {
            if (it.decision() != null) decisionJson = objectMapper.writeValueAsString(it.decision());
        } catch (JsonProcessingException ignored) {}

        ToolCallRecord toolCall = null;
        if (it.toolCallName() != null) {
            String summary = it.toolResultSummary();
            boolean truncated = summary != null && summary.length() > TOOL_RESULT_MAX_LENGTH;
            String bounded = truncate(summary, TOOL_RESULT_MAX_LENGTH);
            toolCall = new ToolCallRecord(it.toolCallName(), it.toolCallArgs(), bounded, truncated);
        }

        PatchResultRecord patchResult = null;
        if (it.appliedPatchResult() != null) {
            var r = it.appliedPatchResult();
            String summary = "Applied " + r.appliedCount() + " file(s)" + (r.results() != null && !r.results().isEmpty()
                    ? ": " + r.results().stream().map(ApplyPatchResponse.FileResult::path).collect(Collectors.joining(", "))
                    : "");
            List<PatchedFileRecord> files = r.results() != null
                    ? r.results().stream()
                    .map(f -> new PatchedFileRecord(f.path(), f.created(), f.beforeSha256(), f.afterSha256(), f.bytesWritten(), null))
                    .toList()
                    : List.of();
            patchResult = new PatchResultRecord(truncate(summary, 500), files);
        }

        VerificationResultRecord verificationResult = null;
        if (it.testRun() != null) {
            var run = it.testRun();
            String stdoutSummary = truncate(run.stdout(), VERIFICATION_OUTPUT_MAX_LENGTH);
            String stderrSummary = truncate(run.stderr(), VERIFICATION_OUTPUT_MAX_LENGTH);
            verificationResult = new VerificationResultRecord(
                    it.verifyCommand() != null ? List.copyOf(it.verifyCommand()) : null,
                    run.exitCode(),
                    run.durationMs(),
                    stdoutSummary,
                    stderrSummary,
                    run.truncated()
            );
        }

        AgentRunIterationRecord record = new AgentRunIterationRecord(
                it.index(),
                Instant.now().toString(),
                it.decision() != null ? it.decision().mode() : null,
                it.decision() != null ? it.decision().plan() : null,
                decisionJson,
                toolCall,
                patchResult,
                verificationResult,
                it.errors() != null && !it.errors().isEmpty() ? List.copyOf(it.errors()) : null
        );
        runStore.appendIteration(runId, record);
    }

    private String buildSystemPrompt() {
        return """
            You are an expert software engineer agent. You operate in a loop: Observe (context below) -> Think -> Act (exactly one action per response) -> then you will see results and can continue.
            Return STRICT JSON only. No markdown, no code fences.
            Schema for your response:
            {
              "mode": "tool" | "edit" | "verify" | "finish",
              "plan": "short plan string",
              "tool": "repo.search" | "repo.readFile" | "repo.listFiles" | "repo.getTree" | "repo.applyPatch" | "exec.runCommand",
              "args": { ... },
              "edits": [ { "path": "...", "rationale": "...", "newContent": "...", "expectedSha256": "..." } ],
              "testCommand": [ "./mvnw", "-B", "-ntp", "test" ],
              "finalSummary": "..."
            }
            Rules:
            - Use "tool" to call one of: repo.search (args: query, maxResults), repo.readFile (path, startLine?, endLine?), repo.listFiles (glob?, maxResults?), repo.getTree (path?, depth?, maxEntries?), repo.applyPatch (use edits in same turn), exec.runCommand (command array).
            - Use "edit" to apply file changes; provide path, rationale, newContent, and expectedSha256 (current file SHA).
            - Use "verify" to run tests (optional testCommand override).
            - Use "finish" when done; set finalSummary.
            - One action per response. Return only valid JSON.
            """;
    }

    private record ToolResult(String summary, int filesReadCount) {}

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "repo.search", "repo.readFile", "repo.listFiles", "repo.getTree", "repo.applyPatch", "exec.runCommand");

    private static boolean isAllowedTool(String tool) {
        return tool != null && ALLOWED_TOOLS.contains(tool.trim());
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeTool(String repoName, String workingDir, Path repoRoot, AgentLoopDecision decision, int filesReadSoFar, boolean allowCreate) throws IOException {
        String tool = decision.tool();
        if (tool == null || tool.isBlank()) {
            throw new IllegalArgumentException("tool is required when mode=tool");
        }
        Map<String, Object> args = decision.args() != null ? decision.args() : Map.of();

        switch (tool) {
            case "repo.search" -> {
                String query = getString(args, "query");
                if (query == null || query.isBlank()) throw new IllegalArgumentException("repo.search requires query");
                int max = getInt(args, "maxResults", 50);
                List<RepoFsService.SearchHit> hits = repoFsService.search(repoRoot, query, max);
                String summary = hits.size() + " hit(s): " + hits.stream().limit(10).map(h -> h.path() + ":" + h.line()).collect(Collectors.joining(", "));
                if (hits.size() > 10) summary += " ...";
                return new ToolResult(summary + "\n" + hits.stream().limit(20).map(h -> h.path() + ":" + h.line() + " " + h.text()).collect(Collectors.joining("\n")), filesReadSoFar);
            }
            case "repo.readFile" -> {
                if (filesReadSoFar >= MAX_FILES_READ_PER_RUN) {
                    throw new IllegalArgumentException("max files read per run exceeded (" + MAX_FILES_READ_PER_RUN + ")");
                }
                String path = getString(args, "path");
                if (path == null || path.isBlank()) throw new IllegalArgumentException("repo.readFile requires path");
                RepoFsService.ReadFileData data = repoFsService.readFile(repoRoot, path);
                List<String> lines = data.lines();
                Integer startLine = getIntObj(args, "startLine");
                Integer endLine = getIntObj(args, "endLine");
                int start = startLine != null && startLine > 0 ? startLine : 1;
                int end = endLine != null && endLine >= start ? endLine : Math.min(lines.size(), start + 500 - 1);
                if (end > lines.size()) end = lines.size();
                List<String> slice = lines.subList(Math.max(0, start - 1), end);
                String content = String.join("\n", slice);
                return new ToolResult("path=" + path + " sha256=" + data.sha256() + " lines " + start + "-" + end + "/" + lines.size() + "\n" + truncate(content, 8000), filesReadSoFar + 1);
            }
            case "repo.listFiles" -> {
                String glob = getString(args, "glob");
                int maxResults = getInt(args, "maxResults", 200);
                List<Path> paths = repoFsService.listFiles(repoRoot, glob != null ? glob : "**/*", maxResults);
                List<String> rel = paths.stream().map(p -> repoRoot.relativize(p.toAbsolutePath()).toString().replace("\\", "/")).toList();
                return new ToolResult(rel.size() + " file(s): " + String.join(", ", rel.stream().limit(50).toList()), filesReadSoFar);
            }
            case "repo.getTree" -> {
                String path = getString(args, "path");
                int depth = getInt(args, "depth", 2);
                int maxEntries = getInt(args, "maxEntries", 500);
                RepoFsService.TreeResult tr = repoFsService.tree(repoRoot, path != null ? path : ".", depth, maxEntries);
                String entries = tr.entries().stream().limit(80).map(e -> e.path() + " " + e.type()).collect(Collectors.joining(", "));
                return new ToolResult("basePath=" + tr.basePath() + " entries: " + entries, filesReadSoFar);
            }
            case "repo.applyPatch" -> {
                List<?> editsRaw = (List<?>) args.get("edits");
                if (editsRaw == null || editsRaw.isEmpty()) throw new IllegalArgumentException("repo.applyPatch requires edits in args");
                List<ApplyPatchRequest.PatchChange> changes = new ArrayList<>();
                for (Object o : editsRaw) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    Map<String, Object> m2 = (Map<String, Object>) m;
                    String p = getString(m2, "path");
                    if (p == null || p.isBlank()) continue;
                    String expSha = getString(m2, "expectedSha256");
                    if (expSha == null || expSha.isBlank()) {
                        RepoFsService.ReadFileData d = repoFsService.readFile(repoRoot, p);
                        expSha = d.sha256();
                    }
                    String newContent = getString(m2, "newContent");
                    if (newContent == null) newContent = "";
                    changes.add(new ApplyPatchRequest.PatchChange(p, expSha, newContent));
                }
                if (changes.isEmpty()) throw new IllegalArgumentException("no valid edits");
                ApplyPatchRequest patchReq = new ApplyPatchRequest(repoName, allowCreate, changes);
                ApplyPatchResponse resp = repoPatchService.apply(repoRoot, patchReq);
                return new ToolResult("Applied " + resp.appliedCount() + " file(s).", filesReadSoFar);
            }
            case "exec.runCommand" -> {
                Object cmdObj = args.get("command");
                List<String> command;
                if (cmdObj instanceof List<?> list) {
                    command = list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank()).toList();
                } else {
                    throw new IllegalArgumentException("exec.runCommand requires command array");
                }
                if (command.isEmpty()) throw new IllegalArgumentException("command is required");
                CommandRunnerService.RunResult run = commandRunnerService.run(repoName, workingDir, command);
                String out = truncate(run.stdout() + "\n" + run.stderr(), 4000);
                return new ToolResult("exitCode=" + run.exitCode() + " durationMs=" + run.durationMs() + "\n" + out, filesReadSoFar);
            }
            default -> throw new IllegalArgumentException("unknown tool: " + tool + ". Allowed: repo.search, repo.readFile, repo.listFiles, repo.getTree, repo.applyPatch, exec.runCommand");
        }
    }

    private static String getString(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o == null ? null : String.valueOf(o).trim();
    }

    private static int getInt(Map<String, Object> m, String key, int def) {
        Object o = m.get(key);
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Integer getIntObj(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String require(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " is required");
        return v;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static void appendToTranscript(StringBuilder sb, String text) {
        sb.append(text);
        if (sb.length() > MAX_TRANSCRIPT_CHARS) {
            sb.delete(0, sb.length() - MAX_TRANSCRIPT_CHARS);
        }
    }

    public static int getDefaultMaxIterations() { return DEFAULT_MAX_ITERATIONS; }
    public static int getDefaultMaxToolCalls() { return DEFAULT_MAX_TOOL_CALLS; }
    public static int getMaxTranscriptChars() { return MAX_TRANSCRIPT_CHARS; }
    public static int getMaxFilesReadPerRun() { return MAX_FILES_READ_PER_RUN; }
}
