package com.rag.backend.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
public class RagRepoConfig {

    private final List<String> repoRoots;

    public RagRepoConfig(Environment environment) {
        String repoRootsEnv = environment.getProperty("RAG_REPO_ROOTS", "");
        if (repoRootsEnv == null || repoRootsEnv.isBlank()) {
            this.repoRoots = Collections.emptyList();
        } else {
            this.repoRoots = Arrays.stream(repoRootsEnv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    public List<String> getRepoRoots() {
        return repoRoots;
    }

    public List<Path> getBaseRepoRoots() {
        return repoRoots.stream()
                .map(Paths::get)
                .map(Path::normalize)
                .map(Path::toAbsolutePath)
                .toList();
    }

    public boolean hasRepoRoots() {
        return !repoRoots.isEmpty();
    }

    public String getFirstRepoRoot() {
        return repoRoots.isEmpty() ? null : repoRoots.get(0);
    }

    @Deprecated
    public String findRepoRootForName(String repoName) {
        Optional<Path> resolved = resolveRepoRoot(repoName);
        return resolved.map(Path::toString).orElse(null);
    }

    public Optional<Path> resolveRepoRoot(String repoName) {
        if (repoName == null || repoName.isBlank()) {
            return Optional.empty();
        }
        if (repoRoots.isEmpty()) {
            return Optional.empty();
        }
        List<String> tried = new ArrayList<>();

        for (String rootStr : repoRoots) {
            if (rootStr.equals(repoName) || rootStr.endsWith("/" + repoName)) {
                Path candidate = Paths.get(rootStr).normalize().toAbsolutePath();
                tried.add(candidate.toString());
                if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }

        for (String rootStr : repoRoots) {
            Path base = Paths.get(rootStr).normalize().toAbsolutePath();
            Path candidate = base.resolve(repoName).normalize().toAbsolutePath();
            tried.add(candidate.toString());
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    public Path resolveRepoRootOrThrow(String repoName) {
        Optional<Path> resolved = resolveRepoRoot(repoName);
        if (resolved.isPresent()) {
            return resolved.get();
        }
        List<String> tried = new ArrayList<>();
        for (String rootStr : repoRoots) {
            if (rootStr.equals(repoName) || rootStr.endsWith("/" + repoName)) {
                tried.add(Paths.get(rootStr).normalize().toAbsolutePath().toString());
            } else {
                tried.add(Paths.get(rootStr).resolve(repoName).normalize().toAbsolutePath().toString());
            }
        }
        throw new IllegalArgumentException(
                "Could not resolve repo root for: " + repoName + ". Tried: " + tried);
    }

    public List<String> getTriedPathsForRepo(String repoName) {
        if (repoName == null || repoName.isBlank() || repoRoots.isEmpty()) {
            return List.of();
        }
        List<String> tried = new ArrayList<>();
        for (String rootStr : repoRoots) {
            if (rootStr.equals(repoName) || rootStr.endsWith("/" + repoName)) {
                tried.add(Paths.get(rootStr).normalize().toAbsolutePath().toString());
            }
            Path candidate = Paths.get(rootStr).resolve(repoName).normalize().toAbsolutePath();
            if (!tried.contains(candidate.toString())) {
                tried.add(candidate.toString());
            }
        }
        return tried;
    }
}
