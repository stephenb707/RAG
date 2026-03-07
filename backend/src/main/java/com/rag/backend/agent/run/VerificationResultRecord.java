package com.rag.backend.agent.run;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationResultRecord(
        List<String> command,
        Integer exitCode,
        Long durationMs,
        String stdoutSummary,
        String stderrSummary,
        Boolean truncated
) {}
