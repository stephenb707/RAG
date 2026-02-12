package com.rag.backend.agent.exec;

import com.rag.backend.agent.dto.ExecRunRequest;
import com.rag.backend.agent.dto.ExecRunResponse;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class RunnerExecClient {

    private final RestClient restClient;

    public RunnerExecClient(@Qualifier("agentRunnerRestClient") RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient);
    }

    public ExecRunResponse run(ExecRunRequest req) {
        // Runner endpoint: POST /exec/run (baseUrl set on RestClient bean)
        return restClient.post()
                .uri("/exec/run")
                .accept(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .body(Objects.requireNonNull(req))
                .retrieve()
                .body(ExecRunResponse.class);
    }
}
