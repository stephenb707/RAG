package com.rag.backend.controller;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.dto.ChatRequest;
import com.rag.backend.dto.ChatResponse;
import com.rag.backend.entity.ChunkEntity;
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

    public ChatController(EmbeddingService embeddingService, ChunkRepo chunkRepo) {
        this.embeddingService = embeddingService;
        this.chunkRepo = chunkRepo;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = "Dummy response: " + request.getMessage();
        return new ChatResponse(reply);
    }

    // NOTE: route is /api/retrieve (not /api/api/retrieve)
    @PostMapping("/retrieve")
    public List<ChunkEntity> retrieve(@RequestBody Map<String, String> body) {

        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("question is required");
        }

        float[] questionEmbedding = embeddingService.embed(question);

        String vectorLiteral = toPgVectorLiteral(questionEmbedding);

        return chunkRepo.searchTopK(vectorLiteral, 5);
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
