package com.rag.backend.indexing;

import java.util.ArrayList;
import java.util.List;

public class Chunker {

    public record Chunk(int chunkIndex, int startLine, int endLine, String content) {}

    private final int maxLines;
    private final int overlapLines;

    public Chunker(int maxLines, int overlapLines) {
        if (maxLines <= 0) throw new IllegalArgumentException("maxLines must be > 0");
        if (overlapLines < 0) throw new IllegalArgumentException("overlapLines must be >= 0");
        if (overlapLines >= maxLines) throw new IllegalArgumentException("overlapLines must be < maxLines");
        this.maxLines = maxLines;
        this.overlapLines = overlapLines;
    }

    public List<Chunk> chunkLines(List<String> lines) {
        List<Chunk> chunks = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return chunks;

        int start = 0;
        int chunkIndex = 0;

        while (start < lines.size()) {
            int endExclusive = Math.min(start + maxLines, lines.size());
            String content = String.join("\n", lines.subList(start, endExclusive));

            // line numbers as 1-based
            int startLine = start + 1;
            int endLine = endExclusive;

            chunks.add(new Chunk(chunkIndex, startLine, endLine, content));
            chunkIndex++;

            if (endExclusive == lines.size()) break;

            start = Math.max(0, endExclusive - overlapLines);
        }

        return chunks;
    }
}
