package com.rag.backend.agent.blastradius;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlastRadiusAnalysis(
        int fileCount,
        int sensitiveFileCount,
        int createdFileCount,
        int blastRadiusScore,
        List<String> reasons,
        boolean requiresExplicitApproval
) {
    public BlastRadiusAnalysis {
        if (reasons == null) reasons = List.of();
    }
}
