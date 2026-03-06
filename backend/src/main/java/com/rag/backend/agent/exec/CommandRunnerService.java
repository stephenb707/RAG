package com.rag.backend.agent.exec;

import com.rag.backend.agent.dto.ExecRunRequest;
import com.rag.backend.agent.dto.ExecRunResponse;
import com.rag.backend.agent.fs.RepoFsService;
import com.rag.backend.config.RagRepoConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CommandRunnerService {
    private static final Logger log = LoggerFactory.getLogger(CommandRunnerService.class);

    private final RepoFsService repoFsService;
    private final AgentExecConfig execConfig;
    private final RestClient runnerClient;
    private final ObjectMapper objectMapper;

    public CommandRunnerService(
            RagRepoConfig ragRepoConfig,
            RepoFsService repoFsService,
            AgentExecConfig execConfig,
            @Qualifier("agentRunnerRestClient") RestClient runnerClient,
            ObjectMapper objectMapper
    ) {
        this.repoFsService = repoFsService;
        this.execConfig = execConfig;
        this.runnerClient = Objects.requireNonNull(runnerClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public RunResult run(String repoName, String workingDir, List<String> command) {
        if (repoName == null || repoName.isBlank()) throw new IllegalArgumentException("repoName is required");
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("command is required");

        Path repoRoot = repoFsService.resolveRepoRoot(repoName);

        String wd = (workingDir == null || workingDir.isBlank()) ? "." : workingDir;
        Path resolvedWorkingDir = repoFsService.resolveSafePath(repoRoot, wd);

        if (!java.nio.file.Files.exists(resolvedWorkingDir) || !java.nio.file.Files.isDirectory(resolvedWorkingDir)) {
            throw new IllegalArgumentException("workingDir does not exist or is not a directory: " + wd);
        }

        execConfig.validateCommand(command);
        List<String> commandToSend = AgentExecConfig.normalizeCommand(command);

        ExecRunRequest req = new ExecRunRequest(repoName, wd, commandToSend);
        String correlationId = UUID.randomUUID().toString();
        String runnerPath = "/exec/run";

        try {
            RunnerHttpResult runnerResult = runnerClient.post()
                    .uri(runnerPath)
                    .header("X-Correlation-Id", correlationId)
                    .accept(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .body(req)
                    .exchange((request, response) -> {
                        byte[] bytes = response.getBody().readAllBytes();
                        return new RunnerHttpResult(response.getStatusCode(), response.getHeaders().getContentType(), bytes);
                    });

            if (!runnerResult.status.is2xxSuccessful()) {
                throw new RunnerBadGatewayException(
                        runnerResult.status,
                        runnerPath,
                        describeBody(runnerResult.contentType, runnerResult.body),
                        null
                );
            }

            if (runnerResult.contentType == null || !runnerResult.contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                throw new RunnerBadGatewayException(
                        HttpStatus.BAD_GATEWAY,
                        runnerPath,
                        "Unexpected content-type: " + runnerResult.contentType + ", body: "
                                + describeBody(runnerResult.contentType, runnerResult.body),
                        null
                );
            }

            ExecRunResponse resp;
            try {
                resp = objectMapper.readValue(runnerResult.body, ExecRunResponse.class);
            } catch (IOException parseEx) {
                throw new RunnerBadGatewayException(
                        HttpStatus.BAD_GATEWAY,
                        runnerPath,
                        "Invalid JSON response body: " + describeBody(runnerResult.contentType, runnerResult.body),
                        parseEx
                );
            }
            if (resp == null) {
                throw new IllegalStateException("Runner returned empty response");
            }

            return new RunResult(
                    resolvedWorkingDir.toString(),
                    resp.exitCode(),
                    resp.durationMs(),
                    resp.stdout(),
                    resp.stderr(),
                    resp.truncated()
            );
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            throw new RunnerBadGatewayException(e.getStatusCode(), runnerPath, body, e);
        } catch (ResourceAccessException e) {
            String msg = String.valueOf(e.getMessage());
            Throwable cause = e.getCause();
        
            boolean isTimeout =
                    msg.contains("Read timed out") ||
                    msg.contains("connect timed out") ||
                    cause instanceof java.net.SocketTimeoutException;
        
            if (isTimeout) {
                log.warn("runner timeout correlationId={}", correlationId, e);
                throw new RunnerTimeoutException(correlationId, e);
            }

            throw new RunnerBadGatewayException(
                    HttpStatus.BAD_GATEWAY,
                    runnerPath,
                    "Runner transport error: " + truncate(msg, 800),
                    e
            );
        } catch (RestClientException e) {
            throw new RunnerBadGatewayException(
                    HttpStatus.BAD_GATEWAY,
                    runnerPath,
                    "Runner client error: " + truncate(e.getMessage(), 500),
                    e
            );
        }
    }

    private static String describeBody(MediaType contentType, byte[] body) {
        String preview = new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
        return "contentType=" + contentType + ", bodyPreview=" + truncate(preview, 1000);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private record RunnerHttpResult(HttpStatusCode status, MediaType contentType, byte[] body) {}

    public record RunResult(
            String resolvedWorkingDir,
            int exitCode,
            long durationMs,
            String stdout,
            String stderr,
            boolean truncated
    ) {}
}
