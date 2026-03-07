package com.rag.backend.agent.verification;

import com.rag.backend.agent.exec.CommandRunnerService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentVerificationService {

    private static final int STDOUT_STDERR_MAX_LENGTH = 4000;

    private final CommandRunnerService commandRunnerService;

    public AgentVerificationService(CommandRunnerService commandRunnerService) {
        this.commandRunnerService = commandRunnerService;
    }

    public VerificationRunResult runStages(
            String repoName,
            String workingDir,
            List<VerificationStageRequest> stages,
            boolean stopOnFirstFailure
    ) {
        if (stages == null || stages.isEmpty()) {
            return new VerificationRunResult(
                    VerificationStatus.skipped,
                    List.of(),
                    null,
                    null
            );
        }

        String wd = (workingDir == null || workingDir.isBlank()) ? "." : workingDir;
        List<VerificationStageResult> results = new ArrayList<>();
        VerificationStatus overallStatus = VerificationStatus.passed;
        String failedStageName = null;
        String failureSummary = null;

        for (VerificationStageRequest stage : stages) {
            if (stage.command() == null || stage.command().isEmpty()) {
                results.add(new VerificationStageResult(
                        stage.name(),
                        List.of(),
                        0,
                        0L,
                        null,
                        null,
                        false,
                        VerificationStatus.skipped
                ));
                continue;
            }

            VerificationStageResult stageResult = runOneStage(repoName, wd, stage);
            results.add(stageResult);

            if (stageResult.status() == VerificationStatus.failed || stageResult.status() == VerificationStatus.error) {
                overallStatus = stageResult.status();
                failedStageName = stage.name();
                failureSummary = buildFailureSummary(stageResult);
                if (stopOnFirstFailure) {
                    break;
                }
            }
        }

        return new VerificationRunResult(overallStatus, results, failedStageName, failureSummary);
    }

    private VerificationStageResult runOneStage(String repoName, String workingDir, VerificationStageRequest stage) {
        try {
            CommandRunnerService.RunResult run = commandRunnerService.run(repoName, workingDir, stage.command());
            VerificationStatus status = run.exitCode() == 0 ? VerificationStatus.passed : VerificationStatus.failed;
            String stdoutSummary = truncate(run.stdout(), STDOUT_STDERR_MAX_LENGTH);
            String stderrSummary = truncate(run.stderr(), STDOUT_STDERR_MAX_LENGTH);
            return new VerificationStageResult(
                    stage.name(),
                    stage.command(),
                    run.exitCode(),
                    run.durationMs(),
                    stdoutSummary,
                    stderrSummary,
                    run.truncated(),
                    status
            );
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new VerificationStageResult(
                    stage.name(),
                    stage.command(),
                    -1,
                    0L,
                    null,
                    truncate(msg, STDOUT_STDERR_MAX_LENGTH),
                    false,
                    VerificationStatus.error
            );
        }
    }

    private static String buildFailureSummary(VerificationStageResult stageResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("Stage '").append(stageResult.name()).append("' failed (exitCode=").append(stageResult.exitCode()).append(").");
        if (stageResult.stderrSummary() != null && !stageResult.stderrSummary().isBlank()) {
            sb.append(" stderr: ").append(truncate(stageResult.stderrSummary(), 1500));
        }
        if (stageResult.stdoutSummary() != null && !stageResult.stdoutSummary().isBlank()) {
            sb.append(" stdout: ").append(truncate(stageResult.stdoutSummary(), 500));
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
