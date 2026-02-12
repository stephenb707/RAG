package com.rag.backend.agent.dto;

public record ListFilesRequest(
        String repoName,
        String glob,
        Integer maxResults
) {}
