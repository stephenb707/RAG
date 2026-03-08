package com.rag.backend.agent.proposal;

import com.rag.backend.agent.dto.ApplyPatchRequest;
import com.rag.backend.agent.dto.AgentProposalResponse;
import com.rag.backend.agent.blastradius.BlastRadiusAnalysis;

import java.util.ArrayList;
import java.util.List;

public record AgentProposalRecord(
        String proposalId,
        String repoName,
        String workingDir,
        String goal,
        String createdAt,
        String status,
        String plan,
        List<ProposedEditEntry> proposedEdits,
        List<ApplyPatchRequest.PatchChange> patchChanges,
        List<AgentProposalResponse.DiffSummaryItem> diffSummaries,
        boolean allowCreate,
        String originatingRunId,
        String parentProposalId,
        String verificationFailureStage,
        String summary,
        List<String> riskFlags,
        boolean requiresApproval,
        BlastRadiusAnalysis blastRadiusAnalysis
) {
    public AgentProposalRecord {
        if (proposedEdits == null) proposedEdits = new ArrayList<>();
        if (patchChanges == null) patchChanges = new ArrayList<>();
        if (diffSummaries == null) diffSummaries = new ArrayList<>();
        if (riskFlags == null) riskFlags = new ArrayList<>();
    }

    public record ProposedEditEntry(String path, String rationale, String newContent) {}
}
