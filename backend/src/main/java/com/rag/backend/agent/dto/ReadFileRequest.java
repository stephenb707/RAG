package com.rag.backend.agent.dto;

public record ReadFileRequest(
        String repoName,
        String path,
        Integer startLine,
        Integer endLine
) {}
