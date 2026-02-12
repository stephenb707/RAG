package com.rag.runner.dto;

import java.util.List;

public record ExecRunRequest(
        String repoName,
        String workingDir,
        List<String> command
) {}
