package com.rag.backend.agent.dto;

import java.util.List;

public record ReadFileResponse(
        String repoName,
        String path,
        String fileSha256,
        int startLine,
        int endLine,
        int totalLines,
        List<String> lines
) {}
