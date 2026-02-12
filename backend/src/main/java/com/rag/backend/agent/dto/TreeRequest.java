package com.rag.backend.agent.dto;

public record TreeRequest(
        String repoName,
        String path,
        Integer depth,
        Integer maxEntries
) {}
