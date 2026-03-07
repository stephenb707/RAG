package com.rag.backend.agent.dto;

import java.util.List;

public record AgentLoopRequest(
        String repoName,
        String workingDir,
        String goal,
        List<String> seedFilePaths,
        List<String> testCommand,
        Integer maxIterations,
        Integer maxToolCalls,
        Boolean allowCreate
) {}
