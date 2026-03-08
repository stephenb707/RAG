package com.rag.backend.agent.controller;

import com.rag.backend.agent.dto.AgentProposalResponse;
import com.rag.backend.agent.proposal.AgentProposalRecord;
import com.rag.backend.agent.proposal.AgentProposalStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent/proposals")
public class AgentProposalController {

    private final AgentProposalStoreService proposalStore;

    public AgentProposalController(AgentProposalStoreService proposalStore) {
        this.proposalStore = proposalStore;
    }

    @GetMapping("/{proposalId}")
    public ResponseEntity<AgentProposalResponse> getProposal(@PathVariable String proposalId) {
        AgentProposalRecord record = proposalStore.get(proposalId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        AgentProposalResponse response = toResponse(record);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AgentProposalResponse>> listProposals(
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<AgentProposalRecord> records = proposalStore.list(Math.min(limit, 100));
        List<AgentProposalResponse> list = records.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    private AgentProposalResponse toResponse(AgentProposalRecord r) {
        List<AgentProposalResponse.ProposedEditSummary> edits = r.proposedEdits().stream()
                .map(e -> new AgentProposalResponse.ProposedEditSummary(e.path(), e.rationale(), e.newContent()))
                .collect(Collectors.toList());
        return new AgentProposalResponse(
                r.proposalId(),
                r.repoName(),
                r.workingDir(),
                r.goal(),
                r.plan(),
                edits,
                r.diffSummaries(),
                r.riskFlags(),
                r.requiresApproval(),
                r.blastRadiusAnalysis(),
                r.summary(),
                r.originatingRunId(),
                r.status()
        );
    }
}
