package com.rag.backend.rag;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.ai.OpenAIChatClient;
import com.rag.backend.dto.Citation;
import com.rag.backend.dto.RagAnswer;
import com.rag.backend.entity.ChunkEntity;
import com.rag.backend.repo.ChunkRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagChatService {
    public enum Mode {
        DEFAULT,
        EXPLAIN_ARCHITECTURE,
        CODE_REVIEW
    }    

    private final EmbeddingService embeddingService;
    private final ChunkRepo chunkRepo;
    private final OpenAIChatClient chatClient;

    public RagChatService(EmbeddingService embeddingService, ChunkRepo chunkRepo, OpenAIChatClient chatClient) {
        this.embeddingService = embeddingService;
        this.chunkRepo = chunkRepo;
        this.chatClient = chatClient;
    }

    public RagAnswer answerQuestion(String question) {
        return answerWithMode(question, Mode.DEFAULT);
    }

    public RagAnswer answerWithMode(String question, Mode mode) {

        float[] questionEmbedding = embeddingService.embed(question);
    
        String queryVec = toPgVectorLiteral(questionEmbedding);
        String fileHint = extractFileHint(question);

        List<ChunkEntity> chunks;
        if (fileHint != null) {
        List<ChunkEntity> inFile = chunkRepo.searchTopKInFile(queryVec, fileHint, 3);
        List<ChunkEntity> global = chunkRepo.searchTopK(queryVec, 5);
        chunks = mergeByIdCap(inFile, global, 5);
        } else {
        chunks = chunkRepo.searchTopK(queryVec, 5);
        }

        if (chunks.isEmpty()) {
            long totalChunks = chunkRepo.countTotalChunks();
            long chunksWithEmbedding = chunkRepo.countChunksWithEmbedding();
            long chunksMissingEmbedding = totalChunks - chunksWithEmbedding;
            
            String diagnosticMessage = String.format(
                    "I couldn't retrieve any embedded context from your repo. " +
                    "This usually means chunks haven't been embedded yet. " +
                    "Run POST /api/reindex or re-run indexing (which now embeds), then try again.\n\n" +
                    "Diagnostic: Total chunks: %d, With embeddings: %d, Missing embeddings: %d",
                    totalChunks, chunksWithEmbedding, chunksMissingEmbedding
            );
            return new RagAnswer(diagnosticMessage, List.of());
        }
    
        String context = buildContext(chunks);

        String citationRule = """
        When you reference code, include citations in this exact format on the same sentence:
        [<filePath>:<start>-<end>]

        Only cite files that appear in the provided CONTEXT.
        """;
    
        String systemPrompt = switch (mode) {
            case EXPLAIN_ARCHITECTURE ->
                    """
                    You are a senior software architect.
                    Explain the high-level architecture of this system
                    using ONLY the provided context.
                    Cite files and line ranges.
                    """ + citationRule;
    
            case CODE_REVIEW ->
                    """
                    You are a senior code reviewer.
                    Provide constructive suggestions for improvement
                    using ONLY the provided context.
                    Cite files and line ranges.
                    """ + citationRule;
    
            default ->
                    """
                    You are a senior software engineer.
                    Only answer using the provided context.
                    You MUST cite file paths and line ranges in your answer.
                    """ + citationRule;
        };
    
        String userPrompt = """
                QUESTION:
                %s
    
                CONTEXT:
                %s
                """.formatted(question, context);
    
        String answer = chatClient.chat(systemPrompt, userPrompt);

        List<Citation> citations = toCitations(chunks);
        citations = filterCitationsByAnswer(answer, citations);
        citations = mergeOverlappingCitations(citations);

        String confidence = buildConfidenceLine(fileHint, chunks);
        String answerWithConfidence = confidence + "\n\n" + answer;
    
        return new RagAnswer(answerWithConfidence, citations);
    }

    private static final Pattern FILE_REF =
    Pattern.compile("([\\w./-]+\\.(java|kt|ts|tsx|js|jsx|py|go|cs|sql|md|yml|yaml))", Pattern.CASE_INSENSITIVE);
    
    private String extractFileHint(String question) {
        var m = FILE_REF.matcher(question);
        return m.find() ? m.group(1) : null;
    }

    private List<ChunkEntity> mergeByIdCap(List<ChunkEntity> primary, List<ChunkEntity> fallback, int cap) {
        java.util.LinkedHashMap<Long, ChunkEntity> map = new java.util.LinkedHashMap<>();
        for (ChunkEntity c : primary) map.putIfAbsent(c.getId(), c);
        for (ChunkEntity c : fallback) map.putIfAbsent(c.getId(), c);
        return map.values().stream().limit(cap).toList();
    }

    private List<Citation> filterCitationsByAnswer(String answer, List<Citation> citations) {
        return citations.stream()
                .filter(c -> answer.contains(c.filePath()))
                .toList();
    }

    private List<Citation> mergeOverlappingCitations(List<Citation> citations) {
        var byFile = citations.stream()
                .collect(java.util.stream.Collectors.groupingBy(Citation::filePath));
    
        java.util.List<Citation> merged = new java.util.ArrayList<>();
    
        for (var entry : byFile.entrySet()) {
            String file = entry.getKey();
            var ranges = entry.getValue().stream()
                    .sorted(java.util.Comparator.comparingInt(Citation::startLine))
                    .toList();
    
            int curStart = -1, curEnd = -1;
            java.util.List<String> snippets = new java.util.ArrayList<>();
    
            for (var c : ranges) {
                if (curStart == -1) {
                    curStart = c.startLine();
                    curEnd = c.endLine();
                    snippets.add(c.snippet());
                    continue;
                }
    
                if (c.startLine() <= curEnd + 1) {
                    curEnd = Math.max(curEnd, c.endLine());
                    snippets.add(c.snippet());
                } else {
                    merged.add(new Citation(file, curStart, curEnd, String.join("\n\n", snippets)));
                    curStart = c.startLine();
                    curEnd = c.endLine();
                    snippets = new java.util.ArrayList<>();
                    snippets.add(c.snippet());
                }
            }
    
            if (curStart != -1) {
                merged.add(new Citation(file, curStart, curEnd, String.join("\n\n", snippets)));
            }
        }
    
        var fileOrder = citations.stream().map(Citation::filePath).distinct().toList();
        merged.sort(java.util.Comparator.comparingInt(c -> fileOrder.indexOf(c.filePath())));
        return merged;
    }

    private String buildConfidenceLine(String fileHint, List<ChunkEntity> chunks) {
        long totalChunks = chunks.size();
        var fileCounts = chunks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getDocument().getFilePath(),
                        java.util.stream.Collectors.counting()
                ));
    
        long uniqueFiles = fileCounts.size();
    
        if (fileHint != null) {
            long matched = fileCounts.entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase().contains(fileHint.toLowerCase()))
                    .mapToLong(e -> e.getValue())
                    .sum();
    
            return "Confidence: Used %d/%d retrieved chunks from **%s** (%d file(s) total)."
                    .formatted(matched, totalChunks, fileHint, uniqueFiles);
        }
    
        return "Confidence: Used %d retrieved chunks from %d file(s)."
                .formatted(totalChunks, uniqueFiles);
    }

    private List<Citation> toCitations(List<ChunkEntity> chunks) {
        return chunks.stream()
                .map(c -> new Citation(
                        c.getDocument().getFilePath(),
                        c.getStartLine(),
                        c.getEndLine(),
                        c.getContent()
                ))
                .toList();
    }
    
    private String buildContext(List<ChunkEntity> chunks) {
        return chunks.stream()
                .map(c ->
                        """
                        ---
                        File: %s
                        Lines: %d-%d
                        Snippet:
                        %s
                        """.formatted(
                                c.getDocument().getFilePath(),
                                c.getStartLine(),
                                c.getEndLine(),
                                c.getContent()
                        )
                )
                .collect(Collectors.joining("\n"));
    }

    private String toPgVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
