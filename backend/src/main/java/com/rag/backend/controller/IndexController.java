package com.rag.backend.controller;

import com.rag.backend.indexing.IndexingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class IndexController {

    private final IndexingService indexingService;

    public IndexController(IndexingService indexingService) {
        this.indexingService = indexingService;
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
}
