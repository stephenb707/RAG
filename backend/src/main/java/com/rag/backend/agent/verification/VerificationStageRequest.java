package com.rag.backend.agent.verification;

import java.util.List;

public record VerificationStageRequest(
        String name,
        List<String> command
) {
    public VerificationStageRequest {
        if (name == null) name = "";
        if (command == null) command = List.of();
    }
}
