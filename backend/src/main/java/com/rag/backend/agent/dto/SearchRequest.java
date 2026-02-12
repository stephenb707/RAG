package com.rag.backend.agent.dto;

public record SearchRequest(
        String repoName,
        String query,
        Integer maxResults
) {}
