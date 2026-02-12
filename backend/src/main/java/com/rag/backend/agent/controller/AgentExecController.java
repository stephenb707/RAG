package com.rag.backend.agent.controller;

import com.rag.backend.agent.dto.ExecRunRequest;
import com.rag.backend.agent.dto.ExecRunResponse;
import com.rag.backend.agent.exec.AgentExecConfig;
import com.rag.backend.agent.exec.CommandRunnerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.List;

@RestController
@RequestMapping("/api/agent/exec")
public class AgentExecController {

    private final CommandRunnerService runner;
    private final AgentExecConfig execConfig;
    private final RestClient runnerClient;
    private final String runnerBaseUrl;

    public AgentExecController(
            CommandRunnerService runner,
            AgentExecConfig execConfig,
            @Qualifier("agentRunnerRestClient") RestClient runnerClient,
            @Value("${agent.runner.base-url:${AGENT_RUNNER_BASE_URL:http://runner:8090}}") String runnerBaseUrl
    ) {
        this.runner = runner;
        this.execConfig = execConfig;
        this.runnerClient = runnerClient;
        this.runnerBaseUrl = runnerBaseUrl == null || runnerBaseUrl.isBlank() ? "http://runner:8090" : runnerBaseUrl.strip().replaceAll("/+$", "");
    }

    @PostMapping("/run")
    public ExecRunResponse run(@RequestBody ExecRunRequest req) {
        var result = runner.run(req.repoName(), req.workingDir(), req.command());

        return new ExecRunResponse(
                req.repoName(),
                (req.workingDir() == null || req.workingDir().isBlank()) ? "." : req.workingDir(),
                req.command(),
                result.exitCode(),
                result.durationMs(),
                result.stdout(),
                result.stderr(),
                result.truncated()
        );
    }

    @GetMapping("/diag")
    public ExecDiagResponse diag() {
        boolean canReachRunnerHealth = false;
        try {
            runnerClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
            canReachRunnerHealth = true;
        } catch (Exception ignored) {
            // any failure -> false
        }
        return new ExecDiagResponse(
                runnerBaseUrl,
                canReachRunnerHealth,
                execConfig.getAllowedCommandsParsed(),
                execConfig.timeoutSeconds(),
                execConfig.maxOutputBytes()
        );
    }

    public record ExecDiagResponse(
            String runnerBaseUrl,
            boolean canReachRunnerHealth,
            List<List<String>> allowedCommandsParsed,
            int timeoutSeconds,
            int maxOutputBytes
    ) {}
}
