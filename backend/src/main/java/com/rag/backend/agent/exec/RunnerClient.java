package com.rag.backend.agent.exec;

import com.rag.backend.agent.dto.ExecRunRequest;
import com.rag.backend.agent.dto.ExecRunResponse;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RunnerClient {

    private final RestClient restClient;

    public RunnerClient(@Qualifier("agentRunnerRestClient") RestClient agentRunnerRestClient) {
        this.restClient = agentRunnerRestClient;
    }

    public ExecRunResponse run(ExecRunRequest req) {
        return restClient.post()
                .uri("/exec/run")
                .accept(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .body(Objects.requireNonNull(req))
                .retrieve()
                .body(ExecRunResponse.class);
    }
}
