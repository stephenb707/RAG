package com.rag.backend.agent.run;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRunRecord(
        String runId,
        String entrypoint,
        String modelName,
        String systemPromptVersion,
        String repoName,
        String workingDir,
        String goal,
        String startedAt,
        String finishedAt,
        String status,
        String finalSummary,
        Boolean finalSummaryTruncated,
        List<AgentRunIterationRecord> iterations
) {
    public AgentRunRecord {
        if (iterations == null) iterations = new ArrayList<>();
    }

    public static AgentRunRecord create(String runId, String repoName, String workingDir, String goal,
                                       String entrypoint, String modelName, String systemPromptVersion) {
        return new AgentRunRecord(
                runId,
                entrypoint != null ? entrypoint : "workflow_loop",
                modelName != null ? modelName : "gpt-4o",
                systemPromptVersion != null ? systemPromptVersion : "v1",
                repoName,
                workingDir,
                goal,
                java.time.Instant.now().toString(),
                null,
                "running",
                null,
                null,
                new ArrayList<>()
        );
    }

    public AgentRunRecord withIteration(AgentRunIterationRecord iteration) {
        List<AgentRunIterationRecord> next = new ArrayList<>(iterations);
        next.add(iteration);
        return new AgentRunRecord(runId, entrypoint, modelName, systemPromptVersion, repoName, workingDir, goal,
                startedAt, finishedAt, status, finalSummary, finalSummaryTruncated, next);
    }

    public AgentRunRecord withFinished(String newStatus, String summary, Boolean summaryTruncated) {
        return new AgentRunRecord(
                runId,
                entrypoint,
                modelName,
                systemPromptVersion,
                repoName,
                workingDir,
                goal,
                startedAt,
                java.time.Instant.now().toString(),
                newStatus != null ? newStatus : status,
                summary != null ? summary : finalSummary,
                summaryTruncated != null ? summaryTruncated : finalSummaryTruncated,
                iterations
        );
    }
}
