package com.rag.backend.agent;

import com.rag.backend.agent.dto.ExecRunRequest;
import com.rag.backend.agent.dto.ExecRunResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasToString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SuppressWarnings({"null", "unchecked"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AgentExecIT.MockRunnerConfig.class)
class AgentExecIT {

    private static String tempRepoPath;
    public static MockRestServiceServer runnerMockServer;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        tempRepoPath = Files.createTempDirectory("agent-exec-it").toAbsolutePath().toString();
        registry.add("RAG_REPO_ROOTS", () -> tempRepoPath);
        registry.add("agent.allowed-commands", () -> "./mvnw -B test|sh -lc \"echo START; sleep 2; echo END\"");
    }

    @Autowired
    org.springframework.boot.test.web.client.TestRestTemplate rest;

    @BeforeEach
    void resetMock() {
        if (runnerMockServer != null) {
            runnerMockServer.reset();
        }
    }

    @Test
    void diag_returnsRunnerConfigAndHealth() {
        if (runnerMockServer != null) {
            runnerMockServer.expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo(Objects.requireNonNull(hasToString(containsString("/actuator/health")))))
                    .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));
        }
        var res = rest.getForEntity("/api/agent/exec/diag", Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = res.getBody();
        assertThat(body).containsKeys("runnerBaseUrl", "canReachRunnerHealth", "allowedCommandsParsed", "timeoutSeconds", "maxOutputBytes");
        assertThat(body.get("runnerBaseUrl")).isNotNull();
        assertThat(body.get("allowedCommandsParsed")).asList().hasSize(2);
        assertThat(body.get("timeoutSeconds")).isNotNull();
        assertThat(body.get("maxOutputBytes")).isNotNull();
        if (runnerMockServer != null) {
            runnerMockServer.verify();
        }
    }

    @Test
    void run_disallowed_returns400WithTokenizedAndAllowlist() {
        var req = new ExecRunRequest("codebase", ".", List.of("curl", "http://evil.com"));
        var res = rest.postForEntity("/api/agent/exec/run", req, Map.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        Map<String, Object> body = res.getBody();
        assertThat(body).containsKey("message");
        String message = (String) body.get("message");
        assertThat(message).contains("Command not allowed");
        assertThat(message).contains("Tokenized command:");
        assertThat(message).contains("[curl, http://evil.com]");
        assertThat(message).contains("allowed (parsed):");
    }

    @Test
    void run_allowed_mockRunner_returns200AndResponse() {
        if (runnerMockServer == null) return;
        String json = """
                {"repoName":"codebase","workingDir":".","command":["sh","-lc","echo START; sleep 2; echo END"],"exitCode":0,"durationMs":100,"stdout":"START\\nEND","stderr":"","truncated":false}
                """;
        runnerMockServer.expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo(Objects.requireNonNull(hasToString(containsString("/exec/run")))))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var req = new ExecRunRequest("codebase", ".", List.of("sh", "-lc", "echo START; sleep 2; echo END"));
        var res = rest.postForEntity("/api/agent/exec/run", req, ExecRunResponse.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        ExecRunResponse response = res.getBody();
        assertThat(response).isNotNull();
        assertThat(response.exitCode()).isZero();
        assertThat(response.stdout()).contains("START");
        assertThat(response.stdout()).contains("END");
        runnerMockServer.verify();
    }

    @TestConfiguration
    static class MockRunnerConfig {
        @Bean("agentRunnerRestClient")
        @Primary
        public RestClient agentRunnerRestClient(
                @Value("${agent.runner.base-url:http://runner:8090}") String baseUrl,
                @Value("${agent.runner.connect-timeout-ms:5000}") int connectMs,
                @Value("${agent.runner.read-timeout-ms:300000}") int readMs
        ) {
            RestClient.Builder builder = RestClient.builder().baseUrl(Objects.requireNonNull(baseUrl == null || baseUrl.isBlank() ? "http://runner:8090" : baseUrl.replaceAll("/+$", "")));
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(Objects.requireNonNull(Duration.ofMillis(connectMs)));
            rf.setReadTimeout(Objects.requireNonNull(Duration.ofMillis(readMs)));
            builder.requestFactory(rf);
            AgentExecIT.runnerMockServer = MockRestServiceServer.bindTo(builder).build();
            return builder.build();
        }
    }
}
