package com.rag.backend.agent.dto;

import java.util.List;

public record AgentChangeWorkflowRequest(
        String repoName,
        String workingDir,
        String goal,
        List<String> filePaths,
        List<String> testCommand
) {}