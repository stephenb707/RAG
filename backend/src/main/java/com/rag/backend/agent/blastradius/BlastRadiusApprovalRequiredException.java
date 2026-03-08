package com.rag.backend.agent.blastradius;

import java.util.List;

public class BlastRadiusApprovalRequiredException extends RuntimeException {

    private final String proposalId;
    private final List<String> blastRadiusReasons;

    public BlastRadiusApprovalRequiredException(String proposalId, List<String> blastRadiusReasons) {
        super("Proposal requires explicit approval due to blast radius: " + (blastRadiusReasons != null ? String.join(", ", blastRadiusReasons) : ""));
        this.proposalId = proposalId;
        this.blastRadiusReasons = blastRadiusReasons != null ? List.copyOf(blastRadiusReasons) : List.of();
    }

    public String getProposalId() {
        return proposalId;
    }

    public List<String> getBlastRadiusReasons() {
        return blastRadiusReasons;
    }
}
