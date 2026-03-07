package com.rag.backend.agent.dto;

import java.util.List;

public record AgentProposalResponse(
        String proposalId,
        String repoName,
        String workingDir,
        String goal,
        String plan,
        List<ProposedEditSummary> proposedEdits,
        List<DiffSummaryItem> diffSummaries,
        List<String> riskFlags,
        boolean requiresApproval,
        String summary,
        String runId,
        String status
) {
    public record ProposedEditSummary(String path, String rationale, String newContent) {}

    public record DiffSummaryItem(
            String path,
            String beforeSha256,
            String afterSha256,
            int addedLines,
            int removedLines,
            String diffSnippet
    ) {}
}
