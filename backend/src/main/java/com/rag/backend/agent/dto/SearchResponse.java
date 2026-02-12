package com.rag.backend.agent.dto;

import java.util.List;

public record SearchResponse(
        String repoName,
        String query,
        List<SearchHitDto> hits
) {
    public record SearchHitDto(
            String path,
            int line,
            String text
    ) {}
}
