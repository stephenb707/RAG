package com.rag.backend.agent.dto;

import com.rag.backend.agent.exec.CommandRunnerService;
import com.rag.backend.agent.verification.VerificationRunResult;

public record AgentApplyProposalResponse(
        String proposalId,
        ApplyPatchResponse applyResult,
        CommandRunnerService.RunResult testResult,
        VerificationRunResult verificationResult,
        String followUpProposalId,
        String failureSummary,
        String finalSummary,
        String runId,
        String status
) {}
