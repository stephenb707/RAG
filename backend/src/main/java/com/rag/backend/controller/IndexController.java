package com.rag.backend.controller;

import com.rag.backend.indexing.IndexingService;
import com.rag.backend.retrieval.ChunkEmbeddingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class IndexController {

    private final IndexingService indexingService;
    private final ChunkEmbeddingService chunkEmbeddingService;

    public IndexController(IndexingService indexingService, ChunkEmbeddingService chunkEmbeddingService) {
        this.indexingService = indexingService;
        this.chunkEmbeddingService = chunkEmbeddingService;
    }

    public record IndexRequest(String repoName, String rootPath) {}
    public record IndexResponse(long repositoryId, int documentsIndexed, int chunksIndexed) {}

    @PostMapping("/index")
    public ResponseEntity<IndexResponse> index(@RequestBody IndexRequest req) throws IOException {
        if (req.repoName() == null || req.repoName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (req.rootPath() == null || req.rootPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        IndexingService.IndexResult result = indexingService.indexFolder(req.repoName(), req.rootPath());
        return ResponseEntity.ok(new IndexResponse(result.repositoryId(), result.documentsIndexed(), result.chunksIndexed()));
    }

    @PostMapping("/reindex")
    public String reindex() {
        int updated = chunkEmbeddingService.backfillMissingEmbeddings(10_000);
        return "Re-indexed " + updated + " chunks";
    }
}
