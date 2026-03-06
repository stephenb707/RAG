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

    /** Base roots as Paths for resolution. Same as repoRoots but as Path list. */
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

    /** @deprecated Use resolveRepoRoot(String) or resolveRepoRootOrThrow(String) for scalable resolution. */
    @Deprecated
    public String findRepoRootForName(String repoName) {
        Optional<Path> resolved = resolveRepoRoot(repoName);
        return resolved.map(Path::toString).orElse(null);
    }

    /**
     * Resolve repoName to an absolute repo root directory.
     * 1) Direct mapping: if a configured root equals or ends with /repoName and exists, use it.
     * 2) Base + repo: for each base root, try baseRoot/repoName if it exists.
     * 3) Otherwise throws with tried paths.
     */
    public Optional<Path> resolveRepoRoot(String repoName) {
        if (repoName == null || repoName.isBlank()) {
            return Optional.empty();
        }
        if (repoRoots.isEmpty()) {
            return Optional.empty();
        }
        List<String> tried = new ArrayList<>();

        // 1) Direct mapping (backwards compatibility)
        for (String rootStr : repoRoots) {
            if (rootStr.equals(repoName) || rootStr.endsWith("/" + repoName)) {
                Path candidate = Paths.get(rootStr).normalize().toAbsolutePath();
                tried.add(candidate.toString());
                if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }

        // 2) Base root + repoName
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

    /**
     * Resolve repoName to repo root. Throws with clear error including tried paths.
     */
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

    /** Return list of paths we tried for diagnostics. */
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
