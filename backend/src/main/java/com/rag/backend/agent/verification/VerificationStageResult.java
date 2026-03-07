package com.rag.backend.agent.verification;

import java.util.List;

public record VerificationStageResult(
        String name,
        List<String> command,
        int exitCode,
        long durationMs,
        String stdoutSummary,
        String stderrSummary,
        boolean truncated,
        VerificationStatus status
) {}
