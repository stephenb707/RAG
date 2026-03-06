package com.rag.backend.agent.util;

public final class JsonExtraction {
    private JsonExtraction() {}

    public static String extractJson(String raw) {
        if (raw == null) return "";

        String s = raw.trim();

        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int endFence = s.lastIndexOf("```");
            if (endFence >= 0) {
                s = s.substring(0, endFence);
            }
            s = s.trim();
        }

        int obj = s.indexOf('{');
        int arr = s.indexOf('[');
        int start = (obj == -1) ? arr : (arr == -1 ? obj : Math.min(obj, arr));
        if (start == -1) return s;

        return s.substring(start).trim();
    }
}
