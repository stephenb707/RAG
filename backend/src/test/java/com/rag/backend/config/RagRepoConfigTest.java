package com.rag.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RagRepoConfigTest {

    private static Path baseRoot;
    private static Path directRepo;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        baseRoot = Files.createTempDirectory("rag-repo-base").toAbsolutePath();
        Path roofingcrm = baseRoot.resolve("roofingcrm");
        Files.createDirectories(roofingcrm);
        directRepo = Files.createTempDirectory("rag-repo-direct").toAbsolutePath();
        // Base root + direct mapping: /repos and /repos/codebase (direct) both work
        registry.add("RAG_REPO_ROOTS", () -> baseRoot.toString() + "," + directRepo.toString());
    }

    @Autowired
    RagRepoConfig config;

    @Test
    void resolveRepoRoot_fromBasePlusRepoName() {
        Optional<Path> resolved = config.resolveRepoRoot("roofingcrm");
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).exists().isDirectory();
        assertThat(resolved.get().toString()).endsWith("roofingcrm");
    }

    @Test
    void resolveRepoRoot_fromDirectMapping() {
        // directRepo is a full path to a repo root (e.g. /tmp/xyz)
        String repoName = directRepo.getFileName().toString();
        Optional<Path> resolved = config.resolveRepoRoot(repoName);
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).exists().isDirectory();
        assertThat(resolved.get()).isEqualTo(directRepo);
    }

    @Test
    void resolveRepoRoot_notFound_throwsWithTriedPaths() {
        assertThatThrownBy(() -> config.resolveRepoRootOrThrow("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not resolve repo root for: nonexistent")
                .hasMessageContaining("Tried:");
    }

    @Test
    void getTriedPathsForRepo_includesBaseAndDirect() {
        List<String> tried = config.getTriedPathsForRepo("roofingcrm");
        assertThat(tried).isNotEmpty();
        assertThat(tried.stream().anyMatch(s -> s.endsWith("roofingcrm"))).isTrue();
    }

    @Test
    void getBaseRepoRoots_returnsNormalizedPaths() {
        List<Path> baseRoots = config.getBaseRepoRoots();
        assertThat(baseRoots).hasSize(2);
        assertThat(baseRoots.get(0)).isAbsolute();
    }
}
