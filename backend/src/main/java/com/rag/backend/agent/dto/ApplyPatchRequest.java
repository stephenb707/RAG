package com.rag.backend.agent.dto;

import java.util.List;

public record ApplyPatchRequest(
        String repoName,
        boolean allowCreate,
        List<PatchChange> changes
) {
    public record PatchChange(
            String path,
            String expectedSha256,
            String newContent
    ) {}
}
