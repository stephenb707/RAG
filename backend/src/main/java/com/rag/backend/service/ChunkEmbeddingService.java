package com.rag.backend.service;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.entity.ChunkEntity;
import com.rag.backend.repo.ChunkRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChunkEmbeddingService {

    private final ChunkRepo chunkRepo;
    private final EmbeddingService embeddingService;

    public ChunkEmbeddingService(ChunkRepo chunkRepo, EmbeddingService embeddingService) {
        this.chunkRepo = chunkRepo;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public void backfillEmbeddings() {
        List<ChunkEntity> chunks = chunkRepo.findAll();

        for (ChunkEntity chunk : chunks) {
            // your entity field is "content"
            float[] embedding = embeddingService.embed(chunk.getContent());

            // pgvector expects a string like: [0.1,0.2,0.3]
            String vectorLiteral = toPgVectorLiteral(embedding);

            chunkRepo.updateEmbedding(chunk.getId(), vectorLiteral);
        }
    }

    @Transactional
    public int backfillMissingEmbeddings(int limit) {
        List<ChunkEntity> chunks = chunkRepo.findTopMissingEmbeddings(limit); // embedding IS NULL
        int updated = 0;

        for (ChunkEntity chunk : chunks) {
            float[] embedding = embeddingService.embed(chunk.getContent());
            String vectorLiteral = toPgVectorLiteral(embedding);
            updated += chunkRepo.updateEmbedding(chunk.getId(), vectorLiteral);
        }

        return updated;
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
