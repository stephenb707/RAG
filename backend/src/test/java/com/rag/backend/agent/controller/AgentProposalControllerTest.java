package com.rag.backend.agent.controller;

import com.rag.backend.agent.dto.AgentProposalResponse;
import com.rag.backend.agent.dto.ApplyPatchRequest;
import com.rag.backend.agent.proposal.AgentProposalRecord;
import com.rag.backend.agent.proposal.AgentProposalStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentProposalController.class)
class AgentProposalControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AgentProposalStoreService proposalStore;

    @Test
    void getProposal_returns_proposal_when_found() throws Exception {
        AgentProposalRecord record = new AgentProposalRecord(
                "prop-1",
                "my-repo",
                ".",
                "Add feature",
                "2025-01-01T00:00:00Z",
                "proposed",
                "Plan",
                List.of(new AgentProposalRecord.ProposedEditEntry("src/Main.java", "r", "content")),
                List.of(new ApplyPatchRequest.PatchChange("src/Main.java", "sha", "content")),
                List.of(new AgentProposalResponse.DiffSummaryItem("src/Main.java", "a", "b", 1, 0, "snippet")),
                false,
                "run-1",
                null,
                null,
                "Summary",
                List.of(),
                false
        );
        when(proposalStore.get("prop-1")).thenReturn(record);

        mvc.perform(get("/api/agent/proposals/prop-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").value("prop-1"))
                .andExpect(jsonPath("$.repoName").value("my-repo"))
                .andExpect(jsonPath("$.goal").value("Add feature"))
                .andExpect(jsonPath("$.status").value("proposed"))
                .andExpect(jsonPath("$.proposedEdits").isArray())
                .andExpect(jsonPath("$.proposedEdits.length()").value(1))
                .andExpect(jsonPath("$.proposedEdits[0].path").value("src/Main.java"));
    }

    @Test
    void getProposal_returns_404_when_not_found() throws Exception {
        when(proposalStore.get("nonexistent")).thenReturn(null);

        mvc.perform(get("/api/agent/proposals/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProposals_returns_list() throws Exception {
        AgentProposalRecord record = new AgentProposalRecord(
                "p1", "r", ".", "g", "2025-01-01T00:00:00Z", "proposed",
                "plan", List.of(), List.of(), List.of(), false, null, null, null, "s", List.of(), false
        );
        when(proposalStore.list(20)).thenReturn(List.of(record));

        mvc.perform(get("/api/agent/proposals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proposalId").value("p1"));
    }

    @Test
    void listProposals_accepts_limit_param() throws Exception {
        when(proposalStore.list(5)).thenReturn(List.of());

        mvc.perform(get("/api/agent/proposals").param("limit", "5"))
                .andExpect(status().isOk());
    }
}
