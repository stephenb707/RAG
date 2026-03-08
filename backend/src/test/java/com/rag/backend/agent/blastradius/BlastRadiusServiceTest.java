package com.rag.backend.agent.blastradius;

import com.rag.backend.agent.dto.ApplyPatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlastRadiusServiceTest {

    private BlastRadiusService service;

    @BeforeEach
    void setUp() {
        service = new BlastRadiusService();
    }

    @Test
    void safe_few_files_no_sensitive_paths_does_not_require_explicit_approval() {
        List<String> paths = List.of("src/Main.java", "src/Util.java");
        List<ApplyPatchRequest.PatchChange> changes = List.of(
                new ApplyPatchRequest.PatchChange("src/Main.java", "sha1", "content1"),
                new ApplyPatchRequest.PatchChange("src/Util.java", "sha2", "content2")
        );
        BlastRadiusAnalysis analysis = service.analyze(paths, changes);

        assertThat(analysis.fileCount()).isEqualTo(2);
        assertThat(analysis.sensitiveFileCount()).isZero();
        assertThat(analysis.createdFileCount()).isZero();
        assertThat(analysis.reasons()).isEmpty();
        assertThat(analysis.requiresExplicitApproval()).isFalse();
    }

    @Test
    void more_than_three_files_requires_explicit_approval() {
        List<String> paths = List.of("a.java", "b.java", "c.java", "d.java");
        BlastRadiusAnalysis analysis = service.analyze(paths, List.of());

        assertThat(analysis.fileCount()).isEqualTo(4);
        assertThat(analysis.reasons()).contains("many_files");
        assertThat(analysis.requiresExplicitApproval()).isTrue();
    }

    @Test
    void touching_auth_requires_explicit_approval() {
        BlastRadiusAnalysis analysis = service.analyze(
                List.of("src/auth/LoginController.java"),
                List.of(new ApplyPatchRequest.PatchChange("src/auth/LoginController.java", "sha", "content"))
        );
        assertThat(analysis.reasons()).anyMatch(r -> r.contains("auth_security"));
        assertThat(analysis.requiresExplicitApproval()).isTrue();
    }

    @Test
    void touching_migration_requires_explicit_approval() {
        BlastRadiusAnalysis analysis = service.analyze(
                List.of("db/migration/V1__init.sql"),
                List.of()
        );
        assertThat(analysis.reasons()).contains("touches_migrations");
        assertThat(analysis.requiresExplicitApproval()).isTrue();
    }

    @Test
    void touching_ci_requires_explicit_approval() {
        BlastRadiusAnalysis analysis = service.analyze(
                List.of(".github/workflows/ci.yml"),
                List.of()
        );
        assertThat(analysis.reasons()).contains("touches_ci_cd");
        assertThat(analysis.requiresExplicitApproval()).isTrue();
    }

    @Test
    void touching_infra_requires_explicit_approval() {
        BlastRadiusAnalysis analysis = service.analyze(
                List.of("infra/terraform/main.tf"),
                List.of()
        );
        assertThat(analysis.reasons()).contains("touches_infra");
        assertThat(analysis.requiresExplicitApproval()).isTrue();
    }

    @Test
    void touching_build_config_requires_explicit_approval() {
        BlastRadiusAnalysis analysis = service.analyze(
                List.of("pom.xml"),
                List.of(new ApplyPatchRequest.PatchChange("pom.xml", "sha", "content"))
        );
        assertThat(analysis.reasons()).contains("touches_build_config");
        assertThat(analysis.requiresExplicitApproval()).isTrue();
    }

    @Test
    void created_file_count_from_patch_changes() {
        List<String> paths = List.of("new/File.java");
        List<ApplyPatchRequest.PatchChange> changes = List.of(
                new ApplyPatchRequest.PatchChange("new/File.java", null, "content")
        );
        BlastRadiusAnalysis analysis = service.analyze(paths, changes);
        assertThat(analysis.createdFileCount()).isEqualTo(1);
    }

    @Test
    void blast_radius_score_includes_sensitive_and_created() {
        List<String> paths = List.of("auth/Login.java", "src/Other.java");
        BlastRadiusAnalysis analysis = service.analyze(paths, List.of());
        assertThat(analysis.blastRadiusScore()).isGreaterThanOrEqualTo(2);
        assertThat(analysis.sensitiveFileCount()).isGreaterThanOrEqualTo(1);
    }
}
