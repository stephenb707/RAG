package com.rag.backend.agent.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AgentExecConfig {
    private static final Logger log = LoggerFactory.getLogger(AgentExecConfig.class);

    private final List<List<String>> allowedCommandsExact;

    private final Set<String> allowedExecutables;
    private final Set<String> mvnAllowedGoals;
    private final Set<String> mvnAllowedFlags;
    private final Set<String> mvnAllowedDProps;

    private final int timeoutSeconds;
    private final int maxOutputBytes;

    public AgentExecConfig(
            @Value("${agent.allowed-commands:${AGENT_ALLOWED_COMMANDS:}}") String allowedCommandsRaw,

            @Value("${agent.allowed-executables:${AGENT_ALLOWED_EXECUTABLES:./mvnw}}") String allowedExecutablesRaw,
            @Value("${agent.mvn.allowed-goals:${AGENT_MVN_ALLOWED_GOALS:test,verify,package}}") String mvnGoalsRaw,
            @Value("${agent.mvn.allowed-flags:${AGENT_MVN_ALLOWED_FLAGS:-B,-ntp,-DskipTests}}") String mvnFlagsRaw,
            @Value("${agent.mvn.allowed-d-props:${AGENT_MVN_ALLOWED_D_PROPS:test}}") String mvnDPropsRaw,

            @Value("${agent.command-timeout-seconds:${AGENT_COMMAND_TIMEOUT_SECONDS:180}}") int timeoutSeconds,
            @Value("${agent.command-max-output-bytes:${AGENT_COMMAND_MAX_OUTPUT_BYTES:200000}}") int maxOutputBytes
    ) {
        this.allowedCommandsExact = parseAllowedExact(allowedCommandsRaw);

        this.allowedExecutables = parseCsvSet(allowedExecutablesRaw);
        this.mvnAllowedGoals = parseCsvSet(mvnGoalsRaw);
        this.mvnAllowedFlags = parseCsvSet(mvnFlagsRaw);
        this.mvnAllowedDProps = parseCsvSet(mvnDPropsRaw);

        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputBytes = maxOutputBytes;
    }

    public int timeoutSeconds() { return timeoutSeconds; }
    public int maxOutputBytes() { return maxOutputBytes; }

    public List<List<String>> getAllowedCommandsParsed() {
        return List.copyOf(allowedCommandsExact);
    }

    public static List<String> normalizeCommand(List<String> cmd) {
        if (cmd == null) return List.of();
        return cmd.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public void validateCommand(List<String> cmd) {
        List<String> normalized = normalizeCommand(cmd);
        if (normalized.isEmpty()) throw new IllegalArgumentException("command is required");

        Decision d = decide(normalized);

        log.info("exec allow decision={} reason={} tokenizedCommand={} execAllow={} mvnGoals={} mvnFlags={} mvnDProps={} exactAllowCount={}",
                d.allowed, d.reason, normalized,
                allowedExecutables, mvnAllowedGoals, mvnAllowedFlags, mvnAllowedDProps,
                allowedCommandsExact.size()
        );

        if (!d.allowed) {
            String msg = "Command not allowed: " + String.join(" ", normalized)
                    + ". Reason: " + d.reason
                    + ". Tokenized command: " + normalized
                    + ". allowedExecutables=" + allowedExecutables
                    + ", mvnAllowedGoals=" + mvnAllowedGoals
                    + ", mvnAllowedFlags=" + mvnAllowedFlags
                    + ", mvnAllowedDProps=" + mvnAllowedDProps
                    + ", allowedExact=" + allowedCommandsExact;
            throw new IllegalArgumentException(msg);
        }
    }

    private Decision decide(List<String> cmd) {
        Decision policy = policyDecision(cmd);
        if (policy.allowed) return policy;

        if (isExactAllowed(cmd)) return new Decision(true, "exact_allowlist_match");

        return policy;
    }

    private Decision policyDecision(List<String> cmd) {
        String exe = cmd.get(0);

        if (!allowedExecutables.contains(exe)) {
            return new Decision(false, "executable_not_allowed(" + exe + ")");
        }

        if ("./mvnw".equals(exe)) {
            return mvnPolicy(cmd);
        }
        return new Decision(false, "no_policy_for_executable(" + exe + ") (use exact allowlist or add policy)");
    }

    private Decision mvnPolicy(List<String> cmd) {
        boolean sawGoal = false;

        for (int i = 1; i < cmd.size(); i++) {
            String tok = cmd.get(i);

            if (tok.startsWith("-")) {
                // allow -Dkey=value or -Dkey
                if (tok.startsWith("-D")) {
                    if (tok.equals("-DskipTests")) {
                        if (!mvnAllowedFlags.contains("-DskipTests")) {
                            return new Decision(false, "mvn_flag_not_allowed(-DskipTests)");
                        }
                        continue;
                    }

                    String d = tok.substring(2); // after "-D"
                    if (d.isBlank()) return new Decision(false, "invalid_mvn_D_property(empty)");

                    String propName = d;
                    int eq = d.indexOf('=');
                    if (eq >= 0) propName = d.substring(0, eq);

                    if (!mvnAllowedDProps.contains(propName)) {
                        return new Decision(false, "mvn_D_property_not_allowed(" + propName + ")");
                    }
                    continue;
                }

                if (!mvnAllowedFlags.contains(tok)) {
                    return new Decision(false, "mvn_flag_not_allowed(" + tok + ")");
                }
                continue;
            }

            sawGoal = true;
            if (!mvnAllowedGoals.contains(tok)) {
                return new Decision(false, "mvn_goal_not_allowed(" + tok + ")");
            }
        }

        if (!sawGoal) {
            return new Decision(false, "mvn_requires_goal");
        }
        return new Decision(true, "mvn_policy_ok");
    }

    private boolean isExactAllowed(List<String> cmd) {
        for (List<String> allowed : allowedCommandsExact) {
            if (allowed.equals(cmd)) return true;
        }
        return false;
    }

    private static Set<String> parseCsvSet(String raw) {
        if (raw == null) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Exact allowlist parsing: pipe-separated commands, each command tokenized (supports quotes)
    private static List<List<String>> parseAllowedExact(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        String[] parts = raw.split("\\|");
        List<List<String>> out = new ArrayList<>();

        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;

            List<String> tokens = tokenizeWithQuotes(trimmed).stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            if (!tokens.isEmpty()) out.add(tokens);
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

    private record Decision(boolean allowed, String reason) {}
}
