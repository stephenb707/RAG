package com.rag.backend.agent.exec;

import org.springframework.http.HttpStatusCode;

public class RunnerBadGatewayException extends RuntimeException {

    private final HttpStatusCode runnerStatus;
    private final String path;
    private final String bodyTruncated;

    public RunnerBadGatewayException(HttpStatusCode runnerStatus, String path, String bodyTruncated, Throwable cause) {
        super("Runner returned " + runnerStatus + " at " + path + ": " + truncate(bodyTruncated, 500), cause);
        this.runnerStatus = runnerStatus;
        this.path = path;
        this.bodyTruncated = bodyTruncated == null ? "" : truncate(bodyTruncated, 1000);
    }

    public HttpStatusCode getRunnerStatus() { return runnerStatus; }
    public String getPath() { return path; }
    public String getBodyTruncated() { return bodyTruncated; }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        return s.substring(0, max) + "...";
    }
}
