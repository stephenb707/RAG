package com.rag.backend.agent.proposal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.backend.agent.dto.AgentProposalResponse;
import com.rag.backend.agent.dto.ApplyPatchRequest;
import com.rag.backend.agent.blastradius.BlastRadiusAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentProposalStoreServiceTest {

    @TempDir
    Path tempDir;

    private AgentProposalStoreService store;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store = new AgentProposalStoreService(tempDir.toString(), objectMapper);
    }

    @Test
    void save_writes_proposal_file() throws Exception {
        AgentProposalRecord record = new AgentProposalRecord(
                "prop-123",
                "my-repo",
                ".",
                "Add feature",
                "2025-01-01T00:00:00Z",
                "proposed",
                "Plan: do X",
                List.of(new AgentProposalRecord.ProposedEditEntry("src/Main.java", "rationale", "content")),
                List.of(new ApplyPatchRequest.PatchChange("src/Main.java", "abc123", "content")),
                List.of(new AgentProposalResponse.DiffSummaryItem("src/Main.java", "abc", "def", 1, 0, "snippet")),
                false,
                "run-456",
                null,
                null,
                "Summary",
                List.of(),
                false,
                null
        );
        store.save(record);

        Path file = tempDir.resolve("proposal-prop-123.json");
        assertThat(Files.isRegularFile(file)).isTrue();
        String json = Files.readString(file);
        assertThat(json).contains("prop-123").contains("my-repo").contains("Add feature").contains("proposed");
    }

    @Test
    void get_returns_saved_proposal() {
        AgentProposalRecord record = new AgentProposalRecord(
                "prop-789",
                "r",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(),
                List.of(new ApplyPatchRequest.PatchChange("p.txt", "sha1", "new")),
                List.of(),
                true,
                null,
                null,
                null,
                "sum",
                List.of("many_files"),
                true,
                null
        );
        store.save(record);

        AgentProposalRecord loaded = store.get("prop-789");
        assertThat(loaded).isNotNull();
        assertThat(loaded.repoName()).isEqualTo("r");
        assertThat(loaded.status()).isEqualTo("proposed");
        assertThat(loaded.patchChanges()).hasSize(1);
        assertThat(loaded.patchChanges().get(0).path()).isEqualTo("p.txt");
        assertThat(loaded.patchChanges().get(0).expectedSha256()).isEqualTo("sha1");
        assertThat(loaded.riskFlags()).containsExactly("many_files");
        assertThat(loaded.requiresApproval()).isTrue();
    }

    @Test
    void get_returns_null_for_unknown_id() {
        assertThat(store.get("nonexistent")).isNull();
    }

    @Test
    void updateStatus_overwrites_status() {
        AgentProposalRecord record = new AgentProposalRecord(
                "prop-update",
                "r", ".", "g", "2025-01-01T00:00:00Z", "proposed",
                "plan", List.of(), List.of(), List.of(), false, null, null, null, "sum", List.of(), false, null
        );
        store.save(record);
        store.updateStatus("prop-update", "applied");

        AgentProposalRecord loaded = store.get("prop-update");
        assertThat(loaded.status()).isEqualTo("applied");
    }

    @Test
    void list_returns_recent_first() {
        AgentProposalRecord r1 = new AgentProposalRecord(
                "p1", "r", ".", "g1", "2025-01-01T00:00:00Z", "proposed",
                "plan", List.of(), List.of(), List.of(), false, null, null, null, "s1", List.of(), false, null
        );
        AgentProposalRecord r2 = new AgentProposalRecord(
                "p2", "r", ".", "g2", "2025-01-01T00:00:00Z", "proposed",
                "plan", List.of(), List.of(), List.of(), false, null, null, null, "s2", List.of(), false, null
        );
        store.save(r1);
        store.save(r2);

        List<AgentProposalRecord> list = store.list(10);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).proposalId()).isEqualTo("p2");
        assertThat(list.get(1).proposalId()).isEqualTo("p1");
    }

    @Test
    void list_respects_limit() {
        store.save(new AgentProposalRecord(
                "a", "r", ".", "g", "2025-01-01T00:00:00Z", "proposed",
                "p", List.of(), List.of(), List.of(), false, null, null, null, "s", List.of(), false, null
        ));
        store.save(new AgentProposalRecord(
                "b", "r", ".", "g", "2025-01-01T00:00:00Z", "proposed",
                "p", List.of(), List.of(), List.of(), false, null, null, null, "s", List.of(), false, null
        ));
        List<AgentProposalRecord> one = store.list(1);
        assertThat(one).hasSize(1);
    }

    @Test
    void save_and_get_persists_blast_radius_analysis() {
        BlastRadiusAnalysis analysis = new BlastRadiusAnalysis(1, 1, 0, 3, List.of("touches_auth_security"), true);
        AgentProposalRecord record = new AgentProposalRecord(
                "prop-blast",
                "r",
                ".",
                "goal",
                "2025-01-01T00:00:00Z",
                "proposed",
                "plan",
                List.of(new AgentProposalRecord.ProposedEditEntry("auth/Login.java", "r", "c")),
                List.of(new ApplyPatchRequest.PatchChange("auth/Login.java", "sha", "c")),
                List.of(),
                false,
                null,
                null,
                null,
                "summary",
                List.of("touches_auth_security"),
                true,
                analysis
        );
        store.save(record);

        AgentProposalRecord loaded = store.get("prop-blast");
        assertThat(loaded).isNotNull();
        assertThat(loaded.blastRadiusAnalysis()).isNotNull();
        assertThat(loaded.blastRadiusAnalysis().fileCount()).isEqualTo(1);
        assertThat(loaded.blastRadiusAnalysis().sensitiveFileCount()).isEqualTo(1);
        assertThat(loaded.blastRadiusAnalysis().blastRadiusScore()).isEqualTo(3);
        assertThat(loaded.blastRadiusAnalysis().reasons()).contains("touches_auth_security");
        assertThat(loaded.blastRadiusAnalysis().requiresExplicitApproval()).isTrue();
    }
}
