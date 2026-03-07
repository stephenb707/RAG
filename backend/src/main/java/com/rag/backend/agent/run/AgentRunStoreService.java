package com.rag.backend.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentRunStoreService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunStoreService.class);
    private static final String FILE_PREFIX = "run-";
    private static final String FILE_SUFFIX = ".json";
    private static final int MAX_SUMMARY_LENGTH = 2000;

    private final Path storageDir;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, AgentRunRecord> activeRuns = new ConcurrentHashMap<>();

    public AgentRunStoreService(
            @Value("${agent.run-storage-dir:${AGENT_RUN_STORAGE_DIR:}}") String storageDirProp,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        Path dir;
        if (storageDirProp != null && !storageDirProp.isBlank()) {
            dir = Paths.get(storageDirProp).toAbsolutePath().normalize();
        } else {
            dir = Paths.get(System.getProperty("java.io.tmpdir", "."), "agent-runs").toAbsolutePath().normalize();
        }
        this.storageDir = dir;
        ensureStorageDir();
        log.info("Agent run storage dir: {}", this.storageDir);
    }

    private void ensureStorageDir() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create run storage directory: " + storageDir, e);
        }
    }

    public String createRun(String repoName, String workingDir, String goal) {
        return createRun(repoName, workingDir, goal, "workflow_loop", "gpt-4o", "v1");
    }

    public String createRun(String repoName, String workingDir, String goal,
                            String entrypoint, String modelName, String systemPromptVersion) {
        String runId = UUID.randomUUID().toString();
        AgentRunRecord record = AgentRunRecord.create(runId, repoName, workingDir, goal, entrypoint, modelName, systemPromptVersion);
        activeRuns.put(runId, record);
        write(record);
        return runId;
    }

    public void appendIteration(String runId, AgentRunIterationRecord iteration) {
        AgentRunRecord current = activeRuns.get(runId);
        if (current == null) {
            current = readFromDisk(runId);
            if (current != null) activeRuns.put(runId, current);
        }
        if (current == null) {
            log.warn("appendIteration: run not found runId={}", runId);
            return;
        }
        AgentRunRecord updated = current.withIteration(iteration);
        activeRuns.put(runId, updated);
        write(updated);
    }

    public void finishRun(String runId, String status, String finalSummary) {
        AgentRunRecord current = activeRuns.get(runId);
        if (current == null) {
            current = readFromDisk(runId);
            if (current != null) activeRuns.put(runId, current);
        }
        if (current == null) {
            log.warn("finishRun: run not found runId={}", runId);
            return;
        }
        String summary = truncate(finalSummary, MAX_SUMMARY_LENGTH);
        boolean truncated = finalSummary != null && finalSummary.length() > MAX_SUMMARY_LENGTH;
        AgentRunRecord updated = current.withFinished(status, summary, truncated);
        activeRuns.put(runId, updated);
        write(updated);
    }

    public AgentRunRecord getRun(String runId) {
        AgentRunRecord inMemory = activeRuns.get(runId);
        if (inMemory != null) return inMemory;
        return readFromDisk(runId);
    }

    public List<AgentRunRecord> listRuns(int limit) {
        ensureStorageDir();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) files.add(p);
            }
        } catch (IOException e) {
            log.warn("listRuns failed", e);
            return List.of();
        }
        files.sort(Comparator.comparing((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toInstant();
            } catch (IOException e) {
                return Instant.EPOCH;
            }
        }).reversed());

        List<AgentRunRecord> result = new ArrayList<>();
        int count = 0;
        for (Path p : files) {
            if (count >= limit) break;
            String name = p.getFileName().toString();
            if (name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)) {
                String runId = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
                AgentRunRecord record = readFromDisk(runId);
                if (record != null) {
                    result.add(record);
                    count++;
                }
            }
        }
        return result;
    }

    Path getStorageDir() {
        return storageDir;
    }

    private void write(AgentRunRecord record) {
        Path file = storageDir.resolve(FILE_PREFIX + record.runId() + FILE_SUFFIX);
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(record);
            Files.writeString(file, json);
        } catch (IOException e) {
            log.error("Failed to write run file runId={}", record.runId(), e);
            throw new UncheckedIOException(e);
        }
    }

    private AgentRunRecord readFromDisk(String runId) {
        Path file = storageDir.resolve(FILE_PREFIX + runId + FILE_SUFFIX);
        if (!Files.isRegularFile(file)) return null;
        try {
            String json = Files.readString(file);
            return objectMapper.readValue(json, AgentRunRecord.class);
        } catch (IOException e) {
            log.warn("Failed to read run file runId={}", runId, e);
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
