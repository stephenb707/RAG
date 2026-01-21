package com.rag.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("OPENAI_API_KEY")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("error", ex.getMessage())
            );
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of("error", ex.getMessage() == null ? "IllegalStateException" : ex.getMessage())
        );
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public ResponseEntity<Map<String, String>> handle401(HttpClientErrorException.Unauthorized e) {
        return ResponseEntity.status(401).body(Map.of(
            "error", "OPENAI_UNAUTHORIZED",
            "message", "OpenAI rejected your API key for this endpoint. Verify the key permissions/project/billing."
        ));
    }

    @ExceptionHandler(HttpClientErrorException.Forbidden.class)
    public ResponseEntity<Map<String, String>> handle403(HttpClientErrorException.Forbidden e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "OPENAI_FORBIDDEN",
                "message", "OpenAI rejected the request (403). The key may lack access to this endpoint/model or the project has restrictions."
        ));
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<Map<String, String>> handle429(HttpClientErrorException.TooManyRequests e) {
        return ResponseEntity.status(429).body(Map.of(
                "error", "OPENAI_RATE_LIMIT_OR_QUOTA",
                "message", "OpenAI quota exceeded or rate-limited. Check billing/limits in your OpenAI project."
        ));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, String>> handleOther4xx(HttpClientErrorException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "OPENAI_HTTP_" + e.getStatusCode().value(),
                "message", "OpenAI request failed with status " + e.getStatusCode().value()
        ));
    }
}
