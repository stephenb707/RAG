package com.rag.backend.agent.dto;

import java.time.Instant;
import java.util.List;

public record TreeResponse(
        String repoName,
        String basePath,
        List<TreeEntryDto> entries
) {
    public record TreeEntryDto(
            String path,
            String type,
            long sizeBytes,
            Instant modifiedAt
    ) {}
}
