package com.rag.backend.agent.run;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRunIterationRecord(
        int index,
        String timestamp,
        String mode,
        String plan,
        String decisionJson,
        ToolCallRecord toolCall,
        PatchResultRecord patchResult,
        VerificationResultRecord verificationResult,
        List<String> errors
) {}
