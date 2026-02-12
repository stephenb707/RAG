package com.rag.backend.agent.dto;

import java.util.List;

public record ExecRunRequest(
        String repoName,
        String workingDir,
        List<String> command
) {}
