package com.rag.backend.agent.proposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Computes risk flags and requiresApproval for a proposal based on
 * number of files and sensitive path segments.
 */
public final class ProposalRiskFlags {

    private static final int RISKY_FILE_COUNT_THRESHOLD = 3;
    private static final List<String> SENSITIVE_PATH_SEGMENTS = List.of(
            "migration", "auth", "security", "ci", "docker", "infra"
    );

    private ProposalRiskFlags() {}

    /**
     * Returns risk flags for the given file paths (e.g. "touches_auth", "many_files").
     * Paths are normalized to forward slashes and lowercased for matching.
     */
    public static List<String> computeRiskFlags(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return List.of();

        List<String> flags = new ArrayList<>();

        if (filePaths.size() > RISKY_FILE_COUNT_THRESHOLD) {
            flags.add("many_files");
        }

        for (String path : filePaths) {
            String normalized = (path == null ? "" : path).replace('\\', '/').toLowerCase(Locale.ROOT);
            for (String segment : SENSITIVE_PATH_SEGMENTS) {
                if (normalized.contains(segment)) {
                    String flag = "touches_" + segment;
                    if (!flags.contains(flag)) {
                        flags.add(flag);
                    }
                }
            }
        }

        return flags;
    }

    /**
     * Returns true if any risk flag is present (approval recommended).
     */
    public static boolean requiresApproval(List<String> riskFlags) {
        return riskFlags != null && !riskFlags.isEmpty();
    }
}
