package com.rag.backend.agent.run;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallRecord(
        String name,
        Object args,
        String resultSummary,
        Boolean truncated
) {}
