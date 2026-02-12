package com.rag.backend.agent.dto;

import java.util.List;

public record ListFilesResponse(
        String repoName,
        String glob,
        List<String> paths
) {}
