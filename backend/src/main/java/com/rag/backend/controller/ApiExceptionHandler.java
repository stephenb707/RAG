package com.rag.backend.controller;

import com.rag.backend.agent.exec.RunnerBadGatewayException;
import com.rag.backend.agent.exec.RunnerTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.rag.backend.agent.fs.PatchConflictException;
import com.rag.backend.agent.blastradius.BlastRadiusApprovalRequiredException;

import java.io.IOException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "bad_request",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(PatchConflictException.class)
    public ResponseEntity<Map<String, String>> handlePatchConflict(PatchConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "conflict", "message", ex.getMessage()));
    }

    @ExceptionHandler(BlastRadiusApprovalRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleBlastRadiusApprovalRequired(BlastRadiusApprovalRequiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "approval_required",
                        "message", ex.getMessage(),
                        "proposalId", ex.getProposalId() != null ? ex.getProposalId() : "",
                        "blastRadiusReasons", ex.getBlastRadiusReasons() != null ? ex.getBlastRadiusReasons() : java.util.List.of()
                ));
    }

    @ExceptionHandler(RunnerBadGatewayException.class)
    public ResponseEntity<Map<String, Object>> handleRunnerBadGateway(RunnerBadGatewayException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "runner_error",
                "message", "Runner returned " + ex.getRunnerStatus() + " at " + ex.getPath(),
                "runnerStatus", ex.getRunnerStatus().toString(),
                "path", ex.getPath(),
                "bodyTruncated", ex.getBodyTruncated()
        ));
    }

    @ExceptionHandler(RunnerTimeoutException.class)
    public ResponseEntity<Map<String, String>> handleRunnerTimeout(RunnerTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "error", "runner_timeout",
                "message", "runner timeout",
                "correlationId", ex.getCorrelationId()
        ));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIo(IOException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "exec_io_error",
                "message", ex.getMessage()
        ));
    }
}
