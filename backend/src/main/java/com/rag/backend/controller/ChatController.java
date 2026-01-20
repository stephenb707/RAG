package com.rag.backend.controller;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.dto.ChatRequest;
import com.rag.backend.dto.ChunkSnippet;
import com.rag.backend.dto.RagAnswer;
import com.rag.backend.rag.RagChatService;
import com.rag.backend.repo.ChunkRepo;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final EmbeddingService embeddingService;
    private final ChunkRepo chunkRepo;
    private final RagChatService ragChatService;

    public ChatController(EmbeddingService embeddingService, ChunkRepo chunkRepo, RagChatService ragChatService) {
        this.embeddingService = embeddingService;
        this.chunkRepo = chunkRepo;
        this.ragChatService = ragChatService;
    }

    @PostMapping("/chat")
    public RagAnswer chat(@RequestBody ChatRequest request) {
        return ragChatService.answerQuestion(request.getMessage());
    }

    @PostMapping("/retrieve")
    public List<ChunkSnippet> retrieve(@RequestBody Map<String, String> body) {

        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("question is required");
        }

        float[] questionEmbedding = embeddingService.embed(question);
        String vectorLiteral = toPgVectorLiteral(questionEmbedding);

        return chunkRepo.searchTopK(vectorLiteral, 5).stream()
                .map(c -> new ChunkSnippet(
                        c.getId(),
                        c.getDocument().getFilePath(),
                        c.getStartLine(),
                        c.getEndLine(),
                        c.getContent()
                ))
                .toList();
    }

    @PostMapping("/chat/explain-architecture")
    public RagAnswer explainArchitecture(@RequestBody ChatRequest request) {
        return ragChatService.answerWithMode(
                request.getMessage(),
                RagChatService.Mode.EXPLAIN_ARCHITECTURE
        );
    }

    @PostMapping("/chat/code-review")
    public RagAnswer codeReview(@RequestBody ChatRequest request) {
        return ragChatService.answerWithMode(
                request.getMessage(),
                RagChatService.Mode.CODE_REVIEW
        );
    }

    private static String toPgVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
