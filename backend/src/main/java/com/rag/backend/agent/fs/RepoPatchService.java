package com.rag.backend.agent.fs;

import com.rag.backend.agent.dto.ApplyPatchRequest;
import com.rag.backend.agent.dto.ApplyPatchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

//Apply small, safe file-based patches scoped to a repo root.
@Service
public class RepoPatchService {

    private final RepoFsService repoFsService;

    private final long maxFileBytes;
    private final int maxPatchFiles;

    public RepoPatchService(
            RepoFsService repoFsService,
            @Value("${agent.max-file-bytes:200000}") long maxFileBytes,
            @Value("${agent.max-patch-files:3}") int maxPatchFiles
    ) {
        this.repoFsService = repoFsService;
        this.maxFileBytes = maxFileBytes;
        this.maxPatchFiles = maxPatchFiles;
    }

    public ApplyPatchResponse apply(Path repoRoot, ApplyPatchRequest req) throws Exception {
        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (req.changes() == null || req.changes().isEmpty()) {
            throw new IllegalArgumentException("changes is required");
        }
        if (req.changes().size() > maxPatchFiles) {
            throw new IllegalArgumentException("Too many files in patch (max " + maxPatchFiles + ")");
        }

        List<ApplyPatchResponse.FileResult> results = new ArrayList<>();

        for (ApplyPatchRequest.PatchChange ch : req.changes()) {
            if (ch == null) continue;
            if (ch.path() == null || ch.path().isBlank()) {
                throw new IllegalArgumentException("Each change requires a path");
            }
            if (ch.newContent() == null) {
                throw new IllegalArgumentException("newContent is required for: " + ch.path());
            }

            Path file = repoFsService.resolveSafePath(repoRoot, ch.path());

            if (repoFsService.isUnderIgnoredDir(repoRoot, file) || !repoFsService.isAllowedFile(file)) {
                throw new IllegalArgumentException("File is not allowed: " + ch.path());
            }

            boolean exists = Files.exists(file);
            boolean created = false;

            String beforeHash = null;
            if (exists) {
                beforeHash = sha256Hex(Files.readAllBytes(file));
                if (ch.expectedSha256() == null || ch.expectedSha256().isBlank()) {
                    throw new IllegalArgumentException("expectedSha256 is required for existing file: " + ch.path());
                }
                if (!beforeHash.equalsIgnoreCase(ch.expectedSha256().trim())) {
                    throw new PatchConflictException(
                            "expectedSha256 mismatch for " + ch.path() + ". expected=" + ch.expectedSha256() + " actual=" + beforeHash
                    );
                }
            } else {
                if (!req.allowCreate()) {
                    throw new PatchConflictException("File does not exist (set allowCreate=true to create): " + ch.path());
                }
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                created = true;
            }

            byte[] newBytes = ch.newContent().getBytes(StandardCharsets.UTF_8);
            if (newBytes.length > maxFileBytes) {
                throw new IllegalArgumentException("File too large (" + newBytes.length + " bytes) for: " + ch.path());
            }

            Files.write(file, newBytes);
            String afterHash = sha256Hex(newBytes);

            results.add(new ApplyPatchResponse.FileResult(
                    ch.path(),
                    created,
                    beforeHash,
                    afterHash,
                    newBytes.length
            ));
        }

        return new ApplyPatchResponse(req.repoName(), results.size(), results);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        return HexFormat.of().formatHex(digest);
    }
}
