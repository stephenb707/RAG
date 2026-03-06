package com.rag.backend.agent.workflow;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DiffSummaryService {

    public DiffSummary summarize(String path, String before, String beforeSha, String after, String afterSha) {
        List<String> a = splitLines(before);
        List<String> b = splitLines(after);

        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }

        int suffix = 0;
        while (suffix < (a.size() - prefix) && suffix < (b.size() - prefix)
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
            suffix++;
        }

        int aMidStart = prefix;
        int aMidEnd = a.size() - suffix;
        int bMidStart = prefix;
        int bMidEnd = b.size() - suffix;

        int removed = Math.max(0, aMidEnd - aMidStart);
        int added = Math.max(0, bMidEnd - bMidStart);

        String snippet = buildSnippet(a, b, aMidStart, aMidEnd, bMidStart, bMidEnd, 3, 220);

        return new DiffSummary(path, beforeSha, afterSha, added, removed, snippet);
    }

    private static List<String> splitLines(String s) {
        if (s == null) return List.of();
        String[] parts = s.split("\\r?\\n", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) out.add(p);
        return out;
    }

    private static String buildSnippet(
            List<String> a, List<String> b,
            int aStart, int aEnd,
            int bStart, int bEnd,
            int context,
            int maxLines
    ) {
        StringBuilder sb = new StringBuilder();
        int aCtxStart = Math.max(0, aStart - context);
        int aCtxEnd = Math.min(a.size(), aEnd + context);

        sb.append("@@ ").append("around line ").append(aStart + 1).append(" @@\n");

        int linesWritten = 0;

        for (int i = aCtxStart; i < aStart && linesWritten < maxLines; i++) {
            sb.append("  ").append(a.get(i)).append("\n");
            linesWritten++;
        }

        for (int i = aStart; i < aEnd && linesWritten < maxLines; i++) {
            sb.append("- ").append(a.get(i)).append("\n");
            linesWritten++;
        }

        for (int i = bStart; i < bEnd && linesWritten < maxLines; i++) {
            sb.append("+ ").append(b.get(i)).append("\n");
            linesWritten++;
        }

        for (int i = aEnd; i < aCtxEnd && linesWritten < maxLines; i++) {
            sb.append("  ").append(a.get(i)).append("\n");
            linesWritten++;
        }

        if (linesWritten >= maxLines) {
            sb.append("... (diff truncated)\n");
        }

        return sb.toString();
    }

    public record DiffSummary(
            String path,
            String beforeSha256,
            String afterSha256,
            int addedLines,
            int removedLines,
            String diffSnippet
    ) {}
}
