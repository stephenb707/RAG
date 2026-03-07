package com.rag.backend.agent.fs;

public class PatchConflictException extends RuntimeException {
    public PatchConflictException(String message) {
        super(message);
    }
}
