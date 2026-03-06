package com.rag.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Strict JSON schema for the LLM loop decision per iteration.
 * Exactly one action per iteration: tool, edit, verify, or finish.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentLoopDecision(
        String mode,           // "tool" | "edit" | "verify" | "finish"
        String plan,          // short plan string
        String tool,          // when mode=tool: repo.search | repo.readFile | repo.listFiles | repo.getTree | repo.applyPatch | exec.runCommand
        Map<String, Object> args,  // tool args when mode=tool
        List<EditItem> edits,     // when mode=edit
        List<String> testCommand, // optional override for verify
        String finalSummary       // when mode=finish
) {
    public record EditItem(
            String path,
            String rationale,
            String newContent,
            String expectedSha256
    ) {}
}
