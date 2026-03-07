package com.rag.backend.agent.verification;

import com.rag.backend.agent.exec.CommandRunnerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentVerificationServiceTest {

    @Mock
    private CommandRunnerService commandRunnerService;

    private AgentVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new AgentVerificationService(commandRunnerService);
    }

    @Test
    void runStages_empty_list_returns_skipped() {
        VerificationRunResult result = verificationService.runStages("repo", ".", List.of(), true);

        assertThat(result.overallStatus()).isEqualTo(VerificationStatus.skipped);
        assertThat(result.stages()).isEmpty();
        verify(commandRunnerService, never()).run(any(), any(), any());
    }

    @Test
    void runStages_runs_stages_in_order() {
        when(commandRunnerService.run(eq("repo"), eq("."), any()))
                .thenReturn(new CommandRunnerService.RunResult(".", 0, 10L, "out1", "", false))
                .thenReturn(new CommandRunnerService.RunResult(".", 0, 20L, "out2", "", false));

        List<VerificationStageRequest> stages = List.of(
                new VerificationStageRequest("lint", List.of("npm", "run", "lint")),
                new VerificationStageRequest("test", List.of("npm", "test"))
        );
        VerificationRunResult result = verificationService.runStages("repo", ".", stages, true);

        assertThat(result.overallStatus()).isEqualTo(VerificationStatus.passed);
        assertThat(result.stages()).hasSize(2);
        assertThat(result.stages().get(0).name()).isEqualTo("lint");
        assertThat(result.stages().get(0).exitCode()).isZero();
        assertThat(result.stages().get(1).name()).isEqualTo("test");
        assertThat(result.stages().get(1).exitCode()).isZero();

        verify(commandRunnerService, times(2)).run(eq("repo"), eq("."), any());
        verify(commandRunnerService).run(eq("repo"), eq("."), eq(List.of("npm", "run", "lint")));
        verify(commandRunnerService).run(eq("repo"), eq("."), eq(List.of("npm", "test")));
    }

    @Test
    void runStages_stops_on_first_failure_when_stopOnFirstFailure_true() {
        when(commandRunnerService.run(eq("repo"), eq("."), eq(List.of("lint", "cmd"))))
                .thenReturn(new CommandRunnerService.RunResult(".", 1, 5L, "", "Lint failed", false));

        List<VerificationStageRequest> stages = List.of(
                new VerificationStageRequest("lint", List.of("lint", "cmd")),
                new VerificationStageRequest("test", List.of("test", "cmd"))
        );
        VerificationRunResult result = verificationService.runStages("repo", ".", stages, true);

        assertThat(result.overallStatus()).isEqualTo(VerificationStatus.failed);
        assertThat(result.failedStageName()).isEqualTo("lint");
        assertThat(result.failureSummary()).contains("Lint failed");
        assertThat(result.stages()).hasSize(1);

        verify(commandRunnerService, times(1)).run(any(), any(), any());
    }

    @Test
    void runStages_continues_after_failure_when_stopOnFirstFailure_false() {
        when(commandRunnerService.run(eq("repo"), eq("."), any()))
                .thenReturn(new CommandRunnerService.RunResult(".", 1, 5L, "", "fail", false))
                .thenReturn(new CommandRunnerService.RunResult(".", 0, 10L, "ok", "", false));

        List<VerificationStageRequest> stages = List.of(
                new VerificationStageRequest("lint", List.of("lint")),
                new VerificationStageRequest("test", List.of("test"))
        );
        VerificationRunResult result = verificationService.runStages("repo", ".", stages, false);

        assertThat(result.overallStatus()).isEqualTo(VerificationStatus.failed);
        assertThat(result.stages()).hasSize(2);
        verify(commandRunnerService, times(2)).run(any(), any(), any());
    }

    @Test
    void runStages_captures_error_status_when_runner_throws() {
        when(commandRunnerService.run(eq("repo"), eq("."), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        List<VerificationStageRequest> stages = List.of(
                new VerificationStageRequest("test", List.of("./mvnw", "test"))
        );
        VerificationRunResult result = verificationService.runStages("repo", ".", stages, true);

        assertThat(result.overallStatus()).isEqualTo(VerificationStatus.error);
        assertThat(result.stages()).hasSize(1);
        assertThat(result.stages().get(0).status()).isEqualTo(VerificationStatus.error);
        assertThat(result.stages().get(0).exitCode()).isEqualTo(-1);
        assertThat(result.failureSummary()).contains("Connection refused");
    }
}
