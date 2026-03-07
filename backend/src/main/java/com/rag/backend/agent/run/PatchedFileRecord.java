package com.rag.backend.agent.run;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatchedFileRecord(
        String path,
        Boolean created,
        String beforeSha256,
        String afterSha256,
        Long bytesWritten,
        String diffSnippet
) {}
