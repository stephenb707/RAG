package com.rag.backend.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public boolean hasRepoRoots() {
        return !repoRoots.isEmpty();
    }

    public String getFirstRepoRoot() {
        return repoRoots.isEmpty() ? null : repoRoots.get(0);
    }

    /**
     * Find a repo root that matches the given repo name (by checking if the folder name matches).
     * Returns the first matching root, or the first root if no match found.
     */
    public String findRepoRootForName(String repoName) {
        if (repoRoots.isEmpty()) {
            return null;
        }
        
        // Try to find a root that ends with the repo name
        for (String root : repoRoots) {
            if (root.endsWith("/" + repoName) || root.equals(repoName)) {
                return root;
            }
        }
        
        // Fallback to first root
        return repoRoots.get(0);
    }
}
