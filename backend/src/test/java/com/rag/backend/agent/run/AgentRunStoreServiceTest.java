package com.rag.backend.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStoreServiceTest {

    @TempDir
    Path tempDir;

    private AgentRunStoreService store;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store = new AgentRunStoreService(tempDir.toString(), objectMapper);
    }

    @Test
    void createRun_writes_file() throws Exception {
        String runId = store.createRun("my-repo", ".", "Add a test");

        assertThat(runId).isNotBlank();
        Path file = tempDir.resolve("run-" + runId + ".json");
        assertThat(Files.isRegularFile(file)).isTrue();
        String json = Files.readString(file);
        assertThat(json).contains("my-repo").contains("Add a test").contains("running");
        assertThat(json).contains("workflow_loop").contains("gpt-4o").contains("v1");
    }

    @Test
    void createRun_with_metadata_persists_entrypoint_model_version() throws Exception {
        String runId = store.createRun("r", ".", "goal", "workflow_change", "gpt-4o-mini", "v2");

        AgentRunRecord record = store.getRun(runId);
        assertThat(record.entrypoint()).isEqualTo("workflow_change");
        assertThat(record.modelName()).isEqualTo("gpt-4o-mini");
        assertThat(record.systemPromptVersion()).isEqualTo("v2");
    }

    @Test
    void appendIteration_updates_file() {
        String runId = store.createRun("r", ".", "goal");
        ToolCallRecord toolCall = new ToolCallRecord("repo.listFiles", java.util.Map.of("maxResults", 10), "2 file(s): a.java, b.java", false);
        AgentRunIterationRecord iter = new AgentRunIterationRecord(
                0, java.time.Instant.now().toString(), "tool", "list files", "{}",
                toolCall, null, null, null);

        store.appendIteration(runId, iter);

        AgentRunRecord record = store.getRun(runId);
        assertThat(record.iterations()).hasSize(1);
        assertThat(record.iterations().get(0).mode()).isEqualTo("tool");
        assertThat(record.iterations().get(0).toolCall()).isNotNull();
        assertThat(record.iterations().get(0).toolCall().name()).isEqualTo("repo.listFiles");
    }

    @Test
    void appendIteration_with_patch_result_persists_file_details() {
        String runId = store.createRun("r", ".", "goal");
        List<PatchedFileRecord> files = List.of(
                new PatchedFileRecord("src/Main.java", false, "abc123", "def456", 1024L, null)
        );
        PatchResultRecord patchResult = new PatchResultRecord("Applied 1 file(s): src/Main.java", files);
        AgentRunIterationRecord iter = new AgentRunIterationRecord(
                0, java.time.Instant.now().toString(), "edit", "apply fix", "{}",
                null, patchResult, null, null);

        store.appendIteration(runId, iter);

        AgentRunRecord record = store.getRun(runId);
        assertThat(record.iterations().get(0).patchResult()).isNotNull();
        assertThat(record.iterations().get(0).patchResult().files()).hasSize(1);
        assertThat(record.iterations().get(0).patchResult().files().get(0).path()).isEqualTo("src/Main.java");
        assertThat(record.iterations().get(0).patchResult().files().get(0).beforeSha256()).isEqualTo("abc123");
        assertThat(record.iterations().get(0).patchResult().files().get(0).afterSha256()).isEqualTo("def456");
        assertThat(record.iterations().get(0).patchResult().files().get(0).bytesWritten()).isEqualTo(1024L);
    }

    @Test
    void appendIteration_with_verification_result_persists_truncation_flag() {
        String runId = store.createRun("r", ".", "goal");
        VerificationResultRecord verification = new VerificationResultRecord(
                List.of("./mvnw", "-B", "test"), 0, 5000L, "Build success", "No errors", true);
        AgentRunIterationRecord iter = new AgentRunIterationRecord(
                0, java.time.Instant.now().toString(), "verify", "run tests", "{}",
                null, null, verification, null);

        store.appendIteration(runId, iter);

        AgentRunRecord record = store.getRun(runId);
        assertThat(record.iterations().get(0).verificationResult()).isNotNull();
        assertThat(record.iterations().get(0).verificationResult().command()).containsExactly("./mvnw", "-B", "test");
        assertThat(record.iterations().get(0).verificationResult().exitCode()).isEqualTo(0);
        assertThat(record.iterations().get(0).verificationResult().truncated()).isTrue();
    }

    @Test
    void finishRun_marks_completed_and_writes_summary() {
        String runId = store.createRun("r", ".", "goal");
        store.finishRun(runId, "finished", "Done.");

        AgentRunRecord record = store.getRun(runId);
        assertThat(record.status()).isEqualTo("finished");
        assertThat(record.finalSummary()).isEqualTo("Done.");
        assertThat(record.finishedAt()).isNotNull();
        assertThat(record.finalSummaryTruncated()).isFalse();
    }

    @Test
    void finishRun_with_long_summary_sets_truncated_flag() {
        String runId = store.createRun("r", ".", "goal");
        String longSummary = "x".repeat(3000);
        store.finishRun(runId, "finished", longSummary);

        AgentRunRecord record = store.getRun(runId);
        assertThat(record.finalSummaryTruncated()).isTrue();
        assertThat(record.finalSummary()).endsWith("...");
    }

    @Test
    void listRuns_returns_recent_sorted_newest_first() {
        String id1 = store.createRun("r1", ".", "g1");
        store.finishRun(id1, "finished", "s1");
        String id2 = store.createRun("r2", ".", "g2");
        store.finishRun(id2, "finished", "s2");

        List<AgentRunRecord> runs = store.listRuns(20);
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).runId()).isEqualTo(id2);
        assertThat(runs.get(1).runId()).isEqualTo(id1);
    }

    @Test
    void getRun_returns_null_for_unknown_id() {
        assertThat(store.getRun("nonexistent-id")).isNull();
    }
}
