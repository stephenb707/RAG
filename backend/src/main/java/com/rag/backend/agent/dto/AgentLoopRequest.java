package com.rag.backend.agent.dto;

import java.util.List;

public record AgentLoopRequest(
        String repoName,           // required
        String workingDir,         // optional, default "."
        String goal,               // required
        List<String> seedFilePaths, // optional, initial context files
        List<String> testCommand,   // optional, default allowed mvn test
        Integer maxIterations,      // optional default 6
        Integer maxToolCalls,       // optional default 25
        Boolean allowCreate         // optional default false, passed to RepoPatchService
) {}
