package com.rag.backend.agent.exec;

public class RunnerTimeoutException extends RuntimeException {

    private final String correlationId;

    public RunnerTimeoutException(String correlationId, Throwable cause) {
        super("runner timeout", cause);
        this.correlationId = correlationId;
    }

    public String getCorrelationId() { return correlationId; }
}
