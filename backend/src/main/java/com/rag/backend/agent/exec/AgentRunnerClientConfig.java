package com.rag.backend.agent.exec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Objects;

@Configuration
public class AgentRunnerClientConfig {

    @Bean
    public RestClient agentRunnerRestClient(
            @Value("${agent.runner.base-url:${AGENT_RUNNER_BASE_URL:http://runner:8090}}") String baseUrl,
            @Value("${agent.runner.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${agent.runner.read-timeout-ms:300000}") int readTimeoutMs
    ) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Objects.requireNonNull(Duration.ofMillis(connectTimeoutMs)));
        rf.setReadTimeout(Objects.requireNonNull(Duration.ofMillis(readTimeoutMs)));
        String url = baseUrl == null || baseUrl.isBlank()
                ? "http://runner:8090"
                : baseUrl.strip().replaceAll("/+$", "");
        return RestClient.builder()
                .baseUrl(Objects.requireNonNull(url))
                .requestFactory(rf)
                .build();
    }
}
