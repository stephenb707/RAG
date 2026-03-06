package com.rag.backend.agent.exec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AgentExecConfigTest {

    private static String tempRepoPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        tempRepoPath = Files.createTempDirectory("agent-exec-config-test").toAbsolutePath().toString();
        registry.add("RAG_REPO_ROOTS", () -> tempRepoPath);
        registry.add("agent.allowed-commands", () -> "./mvnw -B test|sh -lc \"echo START; sleep 2; echo END\"");
    }

    @Autowired
    AgentExecConfig config;

    @Test
    void parseAllowedCommands_returnsTrimmedTokens() {
        List<List<String>> allowed = config.getAllowedCommandsParsed();
        assertThat(allowed).hasSize(2);
        assertThat(allowed.get(0)).containsExactly("./mvnw", "-B", "test");
        assertThat(allowed.get(1)).containsExactly("sh", "-lc", "echo START; sleep 2; echo END");
    }

    @Test
    void validateCommand_exactMatch_allowed() {
        config.validateCommand(List.of("./mvnw", "-B", "test"));
        config.validateCommand(List.of("sh", "-lc", "echo START; sleep 2; echo END"));
    }

    @Test
    void validateCommand_normalizedWithTrim_matches() {
        config.validateCommand(List.of("  ./mvnw  ", "-B", "test"));
        config.validateCommand(List.of("sh", " -lc ", "echo START; sleep 2; echo END"));
    }

    @Test
    void validateCommand_disallowed_throws() {
        assertThatThrownBy(() -> config.validateCommand(List.of("mvn", "test")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command not allowed");
        assertThatThrownBy(() -> config.validateCommand(List.of("./mvnw", "-B")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.validateCommand(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.validateCommand(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateCommand_notAllowed_throwsWithTokenizedAndReason() {
        assertThatThrownBy(() -> config.validateCommand(List.of("curl", "http://evil.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Command not allowed")
                .hasMessageContaining("Tokenized command:")
                .hasMessageContaining("[curl, http://evil.com]");
    }

    @Test
    void normalizeCommand_trimsEachToken() {
        assertThat(AgentExecConfig.normalizeCommand(List.of(" a ", "b", "  c  ")))
                .containsExactly("a", "b", "c");
        assertThat(AgentExecConfig.normalizeCommand(null)).isEmpty();
    }
}
