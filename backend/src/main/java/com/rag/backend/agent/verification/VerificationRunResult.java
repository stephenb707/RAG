package com.rag.backend.agent.verification;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationRunResult(
        VerificationStatus overallStatus,
        List<VerificationStageResult> stages,
        String failedStageName,
        String failureSummary
) {
    public VerificationRunResult {
        if (stages == null) stages = List.of();
    }
}
