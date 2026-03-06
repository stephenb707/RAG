package com.rag.backend.agent.controller;

import com.rag.backend.agent.dto.*;
import com.rag.backend.agent.fs.RepoFsService;
import com.rag.backend.agent.fs.RepoPatchService;
import com.rag.backend.config.RagRepoConfig;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/agent/repo")
public class AgentRepoController {

    private final RepoFsService repoFsService;
    private final RepoPatchService repoPatchService;
    private final RagRepoConfig ragRepoConfig;

    public AgentRepoController(RepoFsService repoFsService, RepoPatchService repoPatchService, RagRepoConfig ragRepoConfig) {
        this.repoFsService = repoFsService;
        this.repoPatchService = repoPatchService;
        this.ragRepoConfig = ragRepoConfig;
    }

    @PostMapping("/tree")
    public ResponseEntity<TreeResponse> tree(@RequestBody TreeRequest req) throws Exception {
        Path repoRoot = repoFsService.resolveRepoRoot(req.repoName());
        int depth = req.depth() == null ? 2 : req.depth();
        int maxEntries = req.maxEntries() == null ? 1000 : req.maxEntries();

        RepoFsService.TreeResult tr = repoFsService.tree(repoRoot, req.path(), depth, maxEntries);

        List<TreeResponse.TreeEntryDto> entries = tr.entries().stream()
                .map(e -> new TreeResponse.TreeEntryDto(e.path(), e.type(), e.sizeBytes(), e.modifiedAt()))
                .toList();

        return ResponseEntity.ok(new TreeResponse(req.repoName(), tr.basePath(), entries));
    }

    @PostMapping("/read")
    public ResponseEntity<ReadFileResponse> read(@RequestBody ReadFileRequest req) throws Exception {
        Path repoRoot = repoFsService.resolveRepoRoot(req.repoName());
        RepoFsService.ReadFileData data = repoFsService.readFile(repoRoot, req.path());

        if (req.path() == null || req.path().isBlank()) {
            throw new IllegalArgumentException("path is required");
        }

        List<String> all = repoFsService.readLines(repoRoot, req.path());
        int total = all.size();

        int start = req.startLine() == null ? 1 : req.startLine();
        int end = req.endLine() == null ? Math.min(total, start + 200 - 1) : req.endLine();

        if (start < 1) start = 1;
        if (end < start) end = start;
        if (end > total) end = total;

        List<String> slice = all.subList(start - 1, end);

        return ResponseEntity.ok(new ReadFileResponse(req.repoName(), req.path(), data.sha256(), start, end, total, slice));
    }

    @PostMapping("/list")
    public ResponseEntity<ListFilesResponse> list(@RequestBody ListFilesRequest req) throws Exception {
        Path repoRoot = repoFsService.resolveRepoRoot(req.repoName());
        int max = req.maxResults() == null ? 200 : req.maxResults();

        List<String> paths = repoFsService.listFiles(repoRoot, req.glob(), max).stream()
                .map(p -> repoRoot.relativize(p.toAbsolutePath()).toString().replace("\\", "/"))
                .toList();

        return ResponseEntity.ok(new ListFilesResponse(req.repoName(), req.glob(), paths));
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest req) throws Exception {
        Path repoRoot = repoFsService.resolveRepoRoot(req.repoName());
        int max = req.maxResults() == null ? 50 : req.maxResults();

        List<SearchResponse.SearchHitDto> hits = repoFsService.search(repoRoot, req.query(), max).stream()
                .map(h -> new SearchResponse.SearchHitDto(h.path(), h.line(), h.text()))
                .toList();

        return ResponseEntity.ok(new SearchResponse(req.repoName(), req.query(), hits));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApplyPatchResponse> apply(@RequestBody ApplyPatchRequest req) throws Exception {
        Path repoRoot = repoFsService.resolveRepoRoot(req.repoName());
        ApplyPatchResponse res = repoPatchService.apply(repoRoot, req);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/diag")
    public ResponseEntity<RepoDiagResponse> diag(@RequestParam String repoName) {
        if (repoName == null || repoName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var resolved = ragRepoConfig.resolveRepoRoot(repoName);
        String resolvedRepoRoot = resolved.map(Path::toString).orElse(null);
        boolean exists = resolved.isPresent();
        List<String> baseRootsTried = ragRepoConfig.getTriedPathsForRepo(repoName);
        return ResponseEntity.ok(new RepoDiagResponse(repoName, resolvedRepoRoot, exists, baseRootsTried));
    }

    @GetMapping("/exists")
    public ResponseEntity<RepoExistsResponse> exists(
            @RequestParam String repoName,
            @RequestParam String path) {
        if (repoName == null || repoName.isBlank() || path == null || path.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Path repoRoot;
        try {
            repoRoot = repoFsService.resolveRepoRoot(repoName);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new RepoExistsResponse(
                    repoName, null, path, null, false, false, false));
        }
        Path resolvedPath;
        try {
            resolvedPath = repoFsService.resolveSafePath(repoRoot, path);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new RepoExistsResponse(
                    repoName, repoRoot.toString(), path, null, false, false, false));
        }
        boolean exists = Files.exists(resolvedPath) && Files.isRegularFile(resolvedPath);
        boolean isAllowed = repoFsService.isAllowedFile(resolvedPath);
        boolean isIgnored = repoFsService.isUnderIgnoredDir(repoRoot, resolvedPath);
        return ResponseEntity.ok(new RepoExistsResponse(
                repoName,
                repoRoot.toString(),
                path,
                resolvedPath.toString(),
                exists,
                isAllowed,
                isIgnored));
    }

    public record RepoDiagResponse(
            String repoName,
            String resolvedRepoRoot,
            boolean exists,
            List<String> baseRootsTried) {}

    public record RepoExistsResponse(
            String repoName,
            String repoRoot,
            String path,
            String resolvedPath,
            boolean exists,
            boolean isAllowed,
            boolean isIgnored) {}
}
