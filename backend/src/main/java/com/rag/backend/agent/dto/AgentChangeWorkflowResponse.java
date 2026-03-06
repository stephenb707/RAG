package com.rag.backend.agent.dto;

import com.rag.backend.agent.exec.CommandRunnerService;
import java.util.List;

public record AgentChangeWorkflowResponse(
        String repoName,
        String workingDir,
        String goal,
        List<ProposedEdit> proposedEdits,
        ApplyPatchResponse applyResult,
        CommandRunnerService.RunResult testRun,
        List<FileDiffSummary> diffs,
        String summary
) {
    public record ProposedEdit(String path, String rationale, String newContent) {}

    public record FileDiffSummary(
            String path,
            String beforeSha256,
            String afterSha256,
            int addedLines,
            int removedLines,
            String diffSnippet
    ) {}
}