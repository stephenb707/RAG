package com.rag.runner.dto;

import java.util.List;

public record ExecRunResponse(
        String repoName,
        String workingDir,
        List<String> command,
        int exitCode,
        long durationMs,
        String stdout,
        String stderr,
        boolean truncated
) {}
