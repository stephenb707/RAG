package com.rag.backend.agent.fs;

//Thrown when a patch precondition fails
public class PatchConflictException extends RuntimeException {
    public PatchConflictException(String message) {
        super(message);
    }
}
