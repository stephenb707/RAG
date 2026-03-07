package com.rag.backend.controller;

import com.rag.backend.agent.exec.RunnerBadGatewayException;
import com.rag.backend.agent.exec.RunnerTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.rag.backend.agent.fs.PatchConflictException;

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
