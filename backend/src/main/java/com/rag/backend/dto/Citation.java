package com.rag.backend.dto;

public record Citation(
    String filePath,
    int startLine,
    int endLine,
    String snippet
) {}