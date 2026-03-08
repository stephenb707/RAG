package com.rag.backend.agent.blastradius;

import com.rag.backend.agent.dto.ApplyPatchRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BlastRadiusService {

    private static final int RISKY_FILE_COUNT_THRESHOLD = 3;

    private static final List<SensitiveCategory> SENSITIVE_CATEGORIES = List.of(
            new SensitiveCategory("migrations", List.of("db/migration", "migration", "flyway", "liquibase", "schema")),
            new SensitiveCategory("ci_cd", List.of(".github", "workflow", "workflows", "ci", "pipelines")),
            new SensitiveCategory("auth_security", List.of("auth", "security", "jwt", "token", "permission")),
            new SensitiveCategory("infra", List.of("docker", "compose", "k8s", "kubernetes", "terraform", "infra")),
            new SensitiveCategory("build_config", List.of("pom.xml", "build.gradle", "package.json",
                    "application.properties", "application.yml", ".env"))
    );

    public BlastRadiusService() {}

    public BlastRadiusAnalysis analyze(List<String> filePaths, List<ApplyPatchRequest.PatchChange> patchChanges) {
        if (filePaths == null) filePaths = List.of();
        if (patchChanges == null) patchChanges = List.of();

        Set<String> createdPaths = new java.util.HashSet<>();
        for (ApplyPatchRequest.PatchChange ch : patchChanges) {
            if (ch != null && ch.path() != null && (ch.expectedSha256() == null || ch.expectedSha256().isBlank())) {
                createdPaths.add(ch.path().replace('\\', '/').trim());
            }
        }

        int fileCount = filePaths.size();
        List<String> reasons = new ArrayList<>();
        int sensitiveCount = 0;

        for (String path : filePaths) {
            String normalized = (path == null ? "" : path).replace('\\', '/').toLowerCase(Locale.ROOT);
            for (SensitiveCategory cat : SENSITIVE_CATEGORIES) {
                if (matchesCategory(normalized, cat)) {
                    String reason = "touches_" + cat.name;
                    if (!reasons.contains(reason)) {
                        reasons.add(reason);
                    }
                    sensitiveCount++;
                    break;
                }
            }
        }

        if (fileCount > RISKY_FILE_COUNT_THRESHOLD) {
            if (!reasons.contains("many_files")) {
                reasons.add("many_files");
            }
        }

        for (String createdPath : createdPaths) {
            String normalized = createdPath.toLowerCase(Locale.ROOT);
            for (SensitiveCategory cat : SENSITIVE_CATEGORIES) {
                if (matchesCategory(normalized, cat)) {
                    String reason = "creates_file_in_" + cat.name;
                    if (!reasons.contains(reason)) {
                        reasons.add(reason);
                    }
                    break;
                }
            }
        }

        int createdFileCount = createdPaths.size();
        int score = fileCount + sensitiveCount * 2 + createdFileCount;
        boolean requiresExplicitApproval = !reasons.isEmpty();

        return new BlastRadiusAnalysis(
                fileCount,
                sensitiveCount,
                createdFileCount,
                score,
                reasons,
                requiresExplicitApproval
        );
    }

    private static boolean matchesCategory(String normalizedPath, SensitiveCategory cat) {
        for (String segment : cat.segments) {
            if (normalizedPath.contains(segment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record SensitiveCategory(String name, List<String> segments) {}
}
