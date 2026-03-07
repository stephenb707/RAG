package com.rag.backend.agent.dto;

import com.rag.backend.agent.verification.VerificationStageRequest;

import java.util.List;

public record AgentApplyProposalRequest(
        String proposalId,
        Boolean runTests,
        Boolean runVerification,
        List<VerificationStageRequest> verificationStages,
        List<String> testCommand
) {}
