package com.rag.backend.agent.workflow;

import com.rag.backend.agent.dto.AgentChangeWorkflowResponse;
import com.rag.backend.agent.dto.AgentProposalResponse;
import com.rag.backend.agent.dto.ApplyPatchRequest;

import java.util.List;

/**
 * Result of generating a proposal without applying changes.
 * Used by the two-step propose-then-apply workflow.
 */
public record ProposalGenerationResult(
        String plan,
        List<AgentChangeWorkflowResponse.ProposedEdit> proposedEdits,
        List<ApplyPatchRequest.PatchChange> patchChanges,
        List<AgentProposalResponse.DiffSummaryItem> diffSummaries
) {}
