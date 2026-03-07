package com.rag.backend.agent.run;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatchResultRecord(
        String summary,
        List<PatchedFileRecord> files
) {}
