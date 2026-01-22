package com.rag.backend.controller;

import com.rag.backend.config.RagRepoConfig;
import com.rag.backend.indexing.IndexingService;
import com.rag.backend.retrieval.ChunkEmbeddingService;
import com.rag.backend.repo.RepositoryRepo;
import com.rag.backend.repo.DocumentRepo;
import com.rag.backend.repo.ChunkRepo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class IndexController {

    private final IndexingService indexingService;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final RagRepoConfig ragRepoConfig;
    private final RepositoryRepo repositoryRepo;
    private final DocumentRepo documentRepo;
    private final ChunkRepo chunkRepo;

    public IndexController(
            IndexingService indexingService,
            ChunkEmbeddingService chunkEmbeddingService,
            RagRepoConfig ragRepoConfig,
            RepositoryRepo repositoryRepo,
            DocumentRepo documentRepo,
            ChunkRepo chunkRepo) {
        this.indexingService = indexingService;
        this.chunkEmbeddingService = chunkEmbeddingService;
        this.ragRepoConfig = ragRepoConfig;
        this.repositoryRepo = repositoryRepo;
        this.documentRepo = documentRepo;
        this.chunkRepo = chunkRepo;
    }

    public record IndexRequest(String repoName, String rootPath) {}
    public record IndexResponse(
            long repositoryId,
            int filesScanned,
            int filesIndexed,
            int filesSkipped,
            int documentsUpserted,
            int chunksCreated,
            int chunksEmbedded,
            long elapsedMsTotal,
            String embeddingError
    ) {}
    public record StatusResponse(
            long repositoryCount,
            long documentCount,
            long chunksTotal,
            long chunksWithEmbedding,
            long chunksMissingEmbedding
    ) {}

    @PostMapping("/index")
    public ResponseEntity<?> index(@RequestBody IndexRequest req) throws IOException {
        if (req.repoName() == null || req.repoName().isBlank()) {
            return ResponseEntity.badRequest().body("repoName is required");
        }

        String rootPath = req.rootPath();
        
        // If rootPath not provided, try to get it from RAG_REPO_ROOTS
        if (rootPath == null || rootPath.isBlank()) {
            if (!ragRepoConfig.hasRepoRoots()) {
                return ResponseEntity.badRequest()
                        .body("rootPath is required when RAG_REPO_ROOTS is not configured");
            }
            // Try to find a matching root by repo name, or use the first one
            rootPath = ragRepoConfig.findRepoRootForName(req.repoName());
            if (rootPath == null) {
                return ResponseEntity.badRequest()
                        .body("Could not determine rootPath from RAG_REPO_ROOTS for repo: " + req.repoName());
            }
        }

        long totalStartTime = System.currentTimeMillis();
        
        try {
            IndexingService.IndexResult result = indexingService.indexFolder(req.repoName(), rootPath);
            
            // Embed chunks for the newly indexed repo
            int chunksEmbedded = 0;
            String embeddingError = null;
            
            try {
                int batchSize = 1000;
                int maxIterations = 50;
                int iterations = 0;
                
                while (iterations < maxIterations) {
                    ChunkEmbeddingService.BackfillResult backfillResult = 
                            chunkEmbeddingService.backfillMissingEmbeddingsForRepo(req.repoName(), batchSize);
                    chunksEmbedded += backfillResult.chunksEmbedded();
                    
                    if (backfillResult.chunksRemaining() == 0) {
                        break;
                    }
                    
                    iterations++;
                }
            } catch (Exception e) {
                embeddingError = "Embedding failed: " + e.getMessage();
                // Log error but don't fail the entire indexing operation
                System.err.println("Error embedding chunks for repo " + req.repoName() + ": " + e.getMessage());
                e.printStackTrace();
            }
            
            long totalElapsedMs = System.currentTimeMillis() - totalStartTime;
            
            return ResponseEntity.ok(new IndexResponse(
                    result.repositoryId(),
                    result.filesScanned(),
                    result.filesIndexed(),
                    result.filesSkipped(),
                    result.documentsUpserted(),
                    result.chunksCreated(),
                    chunksEmbedded,
                    totalElapsedMs,
                    embeddingError
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/index/status")
    public ResponseEntity<StatusResponse> status() {
        long repoCount = repositoryRepo.count();
        long docCount = documentRepo.count();
        long chunksTotal = chunkRepo.countTotalChunks();
        long chunksWithEmbedding = chunkRepo.countChunksWithEmbedding();
        long chunksMissingEmbedding = chunksTotal - chunksWithEmbedding;
        return ResponseEntity.ok(new StatusResponse(
                repoCount, 
                docCount, 
                chunksTotal, 
                chunksWithEmbedding, 
                chunksMissingEmbedding
        ));
    }

    @PostMapping("/reindex")
    public String reindex() {
        int updated = chunkEmbeddingService.backfillMissingEmbeddings(10_000);
        return "Re-indexed " + updated + " chunks";
    }
}
