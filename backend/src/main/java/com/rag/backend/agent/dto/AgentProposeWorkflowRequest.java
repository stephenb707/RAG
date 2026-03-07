package com.rag.backend.agent.dto;

import java.util.List;

public record AgentProposeWorkflowRequest(
        String repoName,
        String workingDir,
        String goal,
        List<String> seedFilePaths,
        List<String> testCommand,
        Boolean allowCreate
) {}
