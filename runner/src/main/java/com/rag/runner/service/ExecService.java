package com.rag.runner.service;

import com.rag.runner.dto.ExecRunRequest;
import com.rag.runner.dto.ExecRunResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

@Service
public class ExecService {

    @Value("${RUNNER_REPO_ROOT:/repos/codebase}")
    private String runnerRepoRoot;

    @Value("${RUNNER_MAX_OUTPUT_BYTES:400000}")
    private int maxOutputBytes;

    @Value("${RUNNER_TIMEOUT_SECONDS:300}")
    private int timeoutSeconds;

    public ExecRunResponse run(ExecRunRequest req) throws Exception {
        if (req == null) throw new IllegalArgumentException("request is required");
        if (req.command() == null || req.command().isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }

        Path repoRoot = resolveRepoRoot();
        Path workingDir = resolveWorkingDir(repoRoot, req.workingDir());

        Instant start = Instant.now();

        ProcessBuilder pb = new ProcessBuilder(req.command());
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(false);

        Process p = pb.start();
        ExecutorService ioPool = Executors.newFixedThreadPool(2);
        Future<StreamCapture> stdoutFuture = ioPool.submit(() -> readBounded(p.getInputStream(), maxOutputBytes));
        Future<StreamCapture> stderrFuture = ioPool.submit(() -> readBounded(p.getErrorStream(), maxOutputBytes));

        boolean timedOut = false;
        int exitCode;
        StreamCapture outCapture;
        StreamCapture errCapture;
        try {
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                timedOut = true;
                terminateProcessTree(p);
                p.waitFor(5, TimeUnit.SECONDS);
            }
            exitCode = timedOut ? 124 : p.exitValue();

            outCapture = awaitCapture(stdoutFuture, timeoutSeconds + 5L);
            errCapture = awaitCapture(stderrFuture, timeoutSeconds + 5L);
        } finally {
            ioPool.shutdownNow();
        }

        String stdout = new String(outCapture.bytes, StandardCharsets.UTF_8);
        String stderr = new String(errCapture.bytes, StandardCharsets.UTF_8);
        if (timedOut) {
            String timeoutMsg = "Command timed out after " + timeoutSeconds + " seconds";
            stderr = stderr == null || stderr.isBlank() ? timeoutMsg : stderr + System.lineSeparator() + timeoutMsg;
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();

        boolean truncated = outCapture.truncated || errCapture.truncated;

        return new ExecRunResponse(
                req.repoName(),
                normalizeRel(repoRoot, workingDir),
                req.command(),
                exitCode,
                durationMs,
                stdout,
                stderr,
                truncated
        );
    }

    private Path resolveRepoRoot() {
        Path root = Paths.get(runnerRepoRoot).normalize().toAbsolutePath();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("RUNNER_REPO_ROOT does not exist or is not a directory: " + root);
        }
        return root;
    }

    private Path resolveWorkingDir(Path repoRoot, String workingDir) {
        String rel = (workingDir == null || workingDir.isBlank()) ? "" : workingDir;

        if (rel.contains("..") || rel.contains("~") || rel.contains("\\")) {
            throw new IllegalArgumentException("Invalid workingDir");
        }

        Path resolved = repoRoot.resolve(rel).normalize().toAbsolutePath();
        if (!resolved.startsWith(repoRoot)) {
            throw new IllegalArgumentException("workingDir escapes repo root");
        }
        if (!Files.exists(resolved) || !Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("workingDir does not exist or is not a directory: " + rel);
        }
        return resolved;
    }

    private static StreamCapture readBounded(InputStream is, int maxBytes) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        boolean truncated = false;
        int r;
        while ((r = is.read(buf)) != -1) {
            int toWrite = Math.min(r, maxBytes - total);
            if (toWrite > 0) {
                baos.write(buf, 0, toWrite);
                total += toWrite;
            }
            if (toWrite < r) {
                truncated = true;
            }
        }
        return new StreamCapture(baos.toByteArray(), truncated);
    }

    private static StreamCapture awaitCapture(Future<StreamCapture> future, long timeoutSeconds) throws Exception {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new StreamCapture(new byte[0], true);
        }
    }

    private static void terminateProcessTree(Process p) {
        ProcessHandle handle = p.toHandle();
        handle.descendants().forEach(desc -> {
            try {
                desc.destroyForcibly();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        });
        try {
            handle.destroyForcibly();
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private static String normalizeRel(Path repoRoot, Path dir) {
        try {
            return repoRoot.relativize(dir.toAbsolutePath()).toString().replace("\\", "/");
        } catch (Exception e) {
            return ".";
        }
    }

    private record StreamCapture(byte[] bytes, boolean truncated) {}
}
