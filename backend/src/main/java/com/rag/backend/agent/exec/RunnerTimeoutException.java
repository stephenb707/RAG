package com.rag.backend.agent.exec;

/**
 * Thrown when the HTTP call to the runner times out. Backend should respond 504 and log correlation id.
 */
public class RunnerTimeoutException extends RuntimeException {

    private final String correlationId;

    public RunnerTimeoutException(String correlationId, Throwable cause) {
        super("runner timeout", cause);
        this.correlationId = correlationId;
    }

    public String getCorrelationId() { return correlationId; }
}
