package com.rag.backend.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.backend.agent.dto.AgentLoopRequest;
import com.rag.backend.agent.dto.AgentLoopResponse;
import com.rag.backend.agent.exec.CommandRunnerService;
import com.rag.backend.agent.fs.RepoFsService;
import com.rag.backend.agent.fs.RepoPatchService;
import com.rag.backend.ai.OpenAIChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentLoopServiceTest {

    @Mock
    private RepoFsService repoFsService;
    @Mock
    private RepoPatchService repoPatchService;
    @Mock
    private CommandRunnerService commandRunnerService;
    @Mock
    private OpenAIChatClient llm;

    private AgentLoopService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        service = new AgentLoopService(repoFsService, repoPatchService, commandRunnerService, llm, objectMapper);
        Path repoRoot = Path.of("/repos/test-repo");
        when(repoFsService.resolveRepoRoot("test-repo")).thenReturn(repoRoot);
    }

    @Test
    void tool_then_tool_then_finish_completes_with_finished_status() throws IOException {
        Path repoRoot = Path.of("/repos/test-repo");
        when(repoFsService.listFiles(eq(repoRoot), anyString(), anyInt()))
                .thenReturn(List.of(repoRoot.resolve("pom.xml"), repoRoot.resolve("src/Main.java")));
        when(repoFsService.readFile(eq(repoRoot), eq("pom.xml")))
                .thenReturn(new RepoFsService.ReadFileData(List.of("<project/>"), "abc123"));

        String decision1 = """
            {"mode":"tool","plan":"list files","tool":"repo.listFiles","args":{"glob":"**/*.java","maxResults":10}}
            """;
        String decision2 = """
            {"mode":"tool","plan":"read pom","tool":"repo.readFile","args":{"path":"pom.xml"}}
            """;
        String decision3 = """
            {"mode":"finish","plan":"done","finalSummary":"Listed and read files."}
            """;

        when(llm.chat(anyString(), anyString()))
                .thenReturn(decision1)
                .thenReturn(decision2)
                .thenReturn(decision3);

        AgentLoopRequest req = new AgentLoopRequest(
                "test-repo", ".", "List Java files and read pom",
                null, null, 6, 25, false);

        AgentLoopResponse response = service.run(req);

        assertThat(response.status()).isEqualTo("finished");
        assertThat(response.finalSummary()).isEqualTo("Listed and read files.");
        assertThat(response.iterations()).hasSize(3);
        assertThat(response.iterations().get(0).decision().mode()).isEqualTo("tool");
        assertThat(response.iterations().get(0).toolCallName()).isEqualTo("repo.listFiles");
        assertThat(response.iterations().get(1).toolCallName()).isEqualTo("repo.readFile");
        assertThat(response.iterations().get(2).decision().mode()).isEqualTo("finish");

        verify(llm, times(3)).chat(anyString(), anyString());
    }

    @Test
    void invalid_json_after_retries_throws_with_message() {
        when(llm.chat(anyString(), anyString()))
                .thenReturn("This is not JSON at all")
                .thenReturn("Still not valid { incomplete")
                .thenReturn("{]");

        AgentLoopRequest req = new AgentLoopRequest("test-repo", ".", "Do something", null, null, 2, 25, false);

        assertThatThrownBy(() -> service.run(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON from model");
    }

    @Test
    void unknown_tool_throws_bad_request() {
        String decision1 = """
            {"mode":"tool","plan":"bad tool","tool":"repo.unknownTool","args":{}}
            """;
        when(llm.chat(anyString(), anyString())).thenReturn(decision1);

        AgentLoopRequest req = new AgentLoopRequest("test-repo", ".", "Try unknown tool", null, null, 6, 25, false);

        assertThatThrownBy(() -> service.run(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown tool");
    }

    @Test
    void max_tool_calls_stops_loop() throws IOException {
        Path repoRoot = Path.of("/repos/test-repo");
        when(repoFsService.listFiles(eq(repoRoot), anyString(), anyInt()))
                .thenReturn(List.of(repoRoot.resolve("a.java")));
        String toolDecision = """
            {"mode":"tool","plan":"list","tool":"repo.listFiles","args":{"maxResults":5}}
            """;
        when(llm.chat(anyString(), anyString())).thenReturn(toolDecision);

        AgentLoopRequest req = new AgentLoopRequest("test-repo", ".", "Loop", null, null, 10, 2, false);

        AgentLoopResponse response = service.run(req);

        assertThat(response.iterations()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.status()).isEqualTo("max_iterations");
        boolean foundMaxExceeded = response.iterations().stream()
                .anyMatch(it -> it.errors() != null && it.errors().stream().anyMatch(e -> e.contains("max_tool_calls")));
        assertThat(foundMaxExceeded).isTrue();
    }
}
