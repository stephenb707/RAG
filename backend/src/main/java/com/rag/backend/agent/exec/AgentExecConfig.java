package com.rag.backend.agent.exec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component
public class AgentExecConfig {
    private static final Logger log = LoggerFactory.getLogger(AgentExecConfig.class);
    private final List<List<String>> allowedCommands;
    private final int timeoutSeconds;
    private final int maxOutputBytes;

    public AgentExecConfig(
            @Value("${agent.allowed-commands:${AGENT_ALLOWED_COMMANDS:}}") String allowedCommandsRaw,
            @Value("${agent.command-timeout-seconds:180}") int timeoutSeconds,
            @Value("${agent.command-max-output-bytes:200000}") int maxOutputBytes
    ) {
        this.allowedCommands = parseAllowed(allowedCommandsRaw);
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputBytes = maxOutputBytes;
    }

    public int timeoutSeconds() { return timeoutSeconds; }
    public int maxOutputBytes() { return maxOutputBytes; }

    /** Parsed allowlist (each entry is a list of tokens). For diag and 400 message. */
    public List<List<String>> getAllowedCommandsParsed() { return List.copyOf(allowedCommands); }

    /** Normalize command tokens (trim) for exact comparison with allowlist. */
    public static List<String> normalizeCommand(List<String> cmd) {
        if (cmd == null) return List.of();
        return cmd.stream().map(String::trim).toList();
    }

    public boolean isAllowed(List<String> cmd) {
        List<String> normalized = normalizeCommand(cmd);
        if (normalized.isEmpty()) return false;
        for (List<String> allowed : allowedCommands) {
            if (allowed.equals(normalized)) return true;
        }
        return false;
    }

    public void validateCommand(List<String> cmd) {
        List<String> normalized = normalizeCommand(cmd);
        if (normalized.isEmpty()) throw new IllegalArgumentException("command is required");
        boolean allowed = isAllowed(normalized);
        log.info("allowlist decision={} tokenizedCommand={} allowedList={}", allowed, normalized, allowedCommands);
        if (!allowed) {
            String msg = "Command not allowed. Tokenized command: " + normalized
                    + ", allowed (parsed): " + allowedCommands;
            throw new IllegalArgumentException(msg);
        }
    }

    private static List<List<String>> parseAllowed(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
    
        String[] parts = raw.split("\\|");
        List<List<String>> out = new ArrayList<>();
    
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
    
            List<String> tokens = tokenizeWithQuotes(trimmed);
            List<String> trimmedTokens = tokens.stream().map(String::trim).toList();
            if (!trimmedTokens.isEmpty()) out.add(trimmedTokens);
        }
    
        return out;
    }

    private static List<String> tokenizeWithQuotes(String s) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
    
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
    
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else if (c == '\\' && i + 1 < s.length()) {
                    // allow escaping quotes like \" or \'
                    char next = s.charAt(i + 1);
                    cur.append(next);
                    i++;
                } else {
                    cur.append(c);
                }
            } else {
                if (Character.isWhitespace(c)) {
                    if (cur.length() > 0) {
                        tokens.add(cur.toString());
                        cur.setLength(0);
                    }
                } else if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteChar = c;
                } else {
                    cur.append(c);
                }
            }
        }
    
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens;
    }
}
