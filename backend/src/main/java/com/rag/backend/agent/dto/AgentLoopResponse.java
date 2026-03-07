package com.rag.backend.agent.dto;

import com.rag.backend.agent.exec.CommandRunnerService;

import java.util.List;

public record AgentLoopResponse(
        String runId,
        String repoName,
        String workingDir,
        String goal,
        List<Iteration> iterations,
        String finalSummary,
        String status
) {
    public record Iteration(
            int index,
            AgentLoopDecision decision,
            String toolCallName,
            Object toolCallArgs,
            String toolResultSummary,
            ApplyPatchResponse appliedPatchResult,
            CommandRunnerService.RunResult testRun,
            List<String> verifyCommand,
            List<String> errors
    ) {}
}
