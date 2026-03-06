package com.rag.runner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RunnerRepoConfig {
    private final List<String> repoRoots;

    private final String legacyRepoRoot;

    public RunnerRepoConfig(
            @Value("${runner.repo-roots:${RUNNER_REPO_ROOTS:}}") String repoRootsRaw,
            @Value("${runner.repo-root:${RUNNER_REPO_ROOT:}}") String legacyRepoRoot
    ) {
        this.repoRoots = parseRoots(repoRootsRaw);
        this.legacyRepoRoot = (legacyRepoRoot == null) ? "" : legacyRepoRoot.trim();
    }

    public List<String> getBaseRepoRoots() {
        List<String> out = new ArrayList<>();

        out.addAll(repoRoots);

        if (out.isEmpty() && !legacyRepoRoot.isBlank()) {
            out.add(legacyRepoRoot);
        }

        return out.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> Paths.get(s).normalize().toAbsolutePath().toString())
                .distinct()
                .collect(Collectors.toList());
    }

    public Path resolveRepoRootOrThrow(String repoName) {
        if (repoName == null || repoName.isBlank()) {
            throw new IllegalArgumentException("repoName is required");
        }

        String name = repoName.trim();
        List<String> bases = getBaseRepoRoots();
        if (bases.isEmpty()) {
            throw new IllegalArgumentException("RUNNER_REPO_ROOTS is not configured");
        }

        for (String base : bases) {
            Path basePath = Paths.get(base).normalize().toAbsolutePath();
            if (endsWithFolderName(basePath, name) && existsDir(basePath)) {
                return basePath;
            }
        }

        for (String base : bases) {
            Path candidate = Paths.get(base).normalize().toAbsolutePath().resolve(name).normalize();
            if (existsDir(candidate)) {
                return candidate;
            }
        }

        throw new IllegalArgumentException("Could not resolve repo root for: " + name + ". Tried: " + getTriedPathsForRepo(name));
    }

    public List<String> getTriedPathsForRepo(String repoName) {
        String name = (repoName == null) ? "" : repoName.trim();
        List<String> bases = getBaseRepoRoots();

        List<String> tried = new ArrayList<>();
        for (String base : bases) {
            Path basePath = Paths.get(base).normalize().toAbsolutePath();

            tried.add(basePath.toString());

            tried.add(basePath.resolve(name).normalize().toString());
        }

        LinkedHashSet<String> uniq = new LinkedHashSet<>(tried);
        return new ArrayList<>(uniq);
    }

    private static boolean existsDir(Path p) {
        return Files.exists(p) && Files.isDirectory(p);
    }

    private static boolean endsWithFolderName(Path p, String folderName) {
        Path fileName = p.getFileName();
        return fileName != null && fileName.toString().equalsIgnoreCase(folderName);
    }

    private static List<String> parseRoots(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
