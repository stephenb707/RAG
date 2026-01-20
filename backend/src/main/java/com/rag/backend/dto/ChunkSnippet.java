package com.rag.backend.dto;

public record ChunkSnippet(
        Long id,
        String filePath,
        int startLine,
        int endLine,
        String content
) {}
