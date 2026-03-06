package com.rag.backend.agent.dto;

import com.rag.backend.agent.exec.CommandRunnerService;

import java.util.List;

public record AgentLoopResponse(
        String repoName,
        String workingDir,
        String goal,
        List<Iteration> iterations,
        String finalSummary,
        String status   // "finished" | "max_iterations" | "error"
) {
    public record Iteration(
            int index,
            AgentLoopDecision decision,
            String toolCallName,      // tool name if mode=tool
            Object toolCallArgs,     // args if mode=tool
            String toolResultSummary,
            ApplyPatchResponse appliedPatchResult,
            CommandRunnerService.RunResult testRun,
            List<String> errors
    ) {}
}
