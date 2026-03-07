package com.rag.backend.agent.proposal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalRiskFlagsTest {

    @Test
    void empty_paths_returns_no_flags() {
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of())).isEmpty();
        assertThat(ProposalRiskFlags.computeRiskFlags(null)).isEmpty();
    }

    @Test
    void few_paths_no_sensitive_segments_returns_no_flags() {
        List<String> paths = List.of("src/Main.java", "src/Util.java");
        assertThat(ProposalRiskFlags.computeRiskFlags(paths)).isEmpty();
    }

    @Test
    void more_than_three_files_sets_many_files_flag() {
        List<String> paths = List.of("a.java", "b.java", "c.java", "d.java");
        assertThat(ProposalRiskFlags.computeRiskFlags(paths)).containsExactly("many_files");
    }

    @Test
    void path_containing_auth_sets_touches_auth() {
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of("src/auth/Login.java")))
                .contains("touches_auth");
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of("src/com/auth/Service.java")))
                .contains("touches_auth");
    }

    @Test
    void path_containing_migration_sets_touches_migration() {
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of("db/migration/V1__init.sql")))
                .contains("touches_migration");
    }

    @Test
    void path_containing_security_sets_touches_security() {
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of("config/security/WebSecurity.java")))
                .contains("touches_security");
    }

    @Test
    void path_containing_infra_sets_touches_infra() {
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of("infra/terraform/main.tf")))
                .contains("touches_infra");
    }

    @Test
    void path_containing_ci_sets_touches_ci() {
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of(".github/workflows/ci.yml")))
                .contains("touches_ci");
    }

    @Test
    void path_containing_docker_sets_touches_docker() {
        assertThat(ProposalRiskFlags.computeRiskFlags(List.of("docker/Dockerfile")))
                .contains("touches_docker");
    }

    @Test
    void multiple_sensitive_paths_accumulate_flags() {
        List<String> paths = List.of("auth/Login.java", "security/Config.java");
        List<String> flags = ProposalRiskFlags.computeRiskFlags(paths);
        assertThat(flags).contains("touches_auth", "touches_security");
    }

    @Test
    void requiresApproval_true_when_any_flag_present() {
        assertThat(ProposalRiskFlags.requiresApproval(List.of("many_files"))).isTrue();
        assertThat(ProposalRiskFlags.requiresApproval(List.of("touches_auth"))).isTrue();
    }

    @Test
    void requiresApproval_false_when_no_flags() {
        assertThat(ProposalRiskFlags.requiresApproval(List.of())).isFalse();
        assertThat(ProposalRiskFlags.requiresApproval(null)).isFalse();
    }
}
