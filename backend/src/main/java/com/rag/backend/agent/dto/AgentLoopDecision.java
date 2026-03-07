package com.rag.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentLoopDecision(
        String mode,
        String plan,
        String tool,
        Map<String, Object> args,
        List<EditItem> edits,
        List<String> testCommand,
        String finalSummary
) {
    public record EditItem(
            String path,
            String rationale,
            String newContent,
            String expectedSha256
    ) {}
}
