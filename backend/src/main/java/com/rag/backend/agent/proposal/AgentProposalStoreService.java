package com.rag.backend.agent.proposal;

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

@Service
public class AgentProposalStoreService {

    private static final Logger log = LoggerFactory.getLogger(AgentProposalStoreService.class);
    private static final String FILE_PREFIX = "proposal-";
    private static final String FILE_SUFFIX = ".json";

    private final Path storageDir;
    private final ObjectMapper objectMapper;

    public AgentProposalStoreService(
            @Value("${agent.proposal-storage-dir:${AGENT_PROPOSAL_STORAGE_DIR:}}") String storageDirProp,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        Path dir;
        if (storageDirProp != null && !storageDirProp.isBlank()) {
            dir = Paths.get(storageDirProp).toAbsolutePath().normalize();
        } else {
            dir = Paths.get(System.getProperty("java.io.tmpdir", "."), "agent-proposals").toAbsolutePath().normalize();
        }
        this.storageDir = dir;
        ensureStorageDir();
        log.info("Agent proposal storage dir: {}", this.storageDir);
    }

    private void ensureStorageDir() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create proposal storage directory: " + storageDir, e);
        }
    }

    public String save(AgentProposalRecord record) {
        ensureStorageDir();
        Path file = storageDir.resolve(FILE_PREFIX + record.proposalId() + FILE_SUFFIX);
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(record);
            Files.writeString(file, json);
        } catch (IOException e) {
            log.error("Failed to write proposal file proposalId={}", record.proposalId(), e);
            throw new UncheckedIOException(e);
        }
        return record.proposalId();
    }

    public String generateProposalId() {
        return UUID.randomUUID().toString();
    }

    public AgentProposalRecord get(String proposalId) {
        Path file = storageDir.resolve(FILE_PREFIX + proposalId + FILE_SUFFIX);
        if (!Files.isRegularFile(file)) return null;
        try {
            String json = Files.readString(file);
            return objectMapper.readValue(json, AgentProposalRecord.class);
        } catch (IOException e) {
            log.warn("Failed to read proposal file proposalId={}", proposalId, e);
            return null;
        }
    }

    public void updateStatus(String proposalId, String status) {
        AgentProposalRecord existing = get(proposalId);
        if (existing == null) {
            log.warn("updateStatus: proposal not found proposalId={}", proposalId);
            return;
        }
        AgentProposalRecord updated = new AgentProposalRecord(
                existing.proposalId(),
                existing.repoName(),
                existing.workingDir(),
                existing.goal(),
                existing.createdAt(),
                status,
                existing.plan(),
                existing.proposedEdits(),
                existing.patchChanges(),
                existing.diffSummaries(),
                existing.allowCreate(),
                existing.originatingRunId(),
                existing.parentProposalId(),
                existing.verificationFailureStage(),
                existing.summary(),
                existing.riskFlags(),
                existing.requiresApproval(),
                existing.blastRadiusAnalysis()
        );
        save(updated);
    }

    public List<AgentProposalRecord> list(int limit) {
        ensureStorageDir();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) files.add(p);
            }
        } catch (IOException e) {
            log.warn("list proposals failed", e);
            return List.of();
        }
        files.sort(Comparator.comparing((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toInstant();
            } catch (IOException e) {
                return Instant.EPOCH;
            }
        }).reversed());

        List<AgentProposalRecord> result = new ArrayList<>();
        int count = 0;
        for (Path p : files) {
            if (count >= limit) break;
            String name = p.getFileName().toString();
            if (name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)) {
                String id = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
                AgentProposalRecord record = get(id);
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
}
