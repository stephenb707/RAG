package com.rag.backend.agent.dto;

import java.util.List;

public record ApplyPatchResponse(
        String repoName,
        int appliedCount,
        List<FileResult> results
) {
    public record FileResult(
            String path,
            boolean created,
            String beforeSha256,
            String afterSha256,
            long bytesWritten
    ) {}
}
