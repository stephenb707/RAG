package com.rag.backend;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.service.ChunkEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EmbeddingBackfillIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired ChunkEmbeddingService chunkEmbeddingService;

    @MockBean EmbeddingService embeddingService;

    private long repoId;
    private long docId;

    @BeforeEach
    void setup() {
        Long repoIdLong = jdbc.queryForObject(
                "INSERT INTO repositories(name, root_path) VALUES ('test-repo','/tmp') RETURNING id",
                Long.class
        );
        repoId = Objects.requireNonNull(repoIdLong, "Repository ID must not be null");
        
        Long docIdLong = jdbc.queryForObject(
                "INSERT INTO documents(repository_id, file_path, content_hash) VALUES (?, 'file.txt', 'h') RETURNING id",
                Long.class,
                repoId
        );
        docId = Objects.requireNonNull(docIdLong, "Document ID must not be null");

        jdbc.update("DELETE FROM chunks WHERE document_id = ?", docId);

        // Create chunks with NULL embeddings
        jdbc.update("""
            INSERT INTO chunks(document_id, chunk_index, start_line, end_line, content, content_hash, embedding)
            VALUES (?, 0, 1, 1, 'chunk one', 'h1', NULL),
                   (?, 1, 1, 1, 'chunk two', 'h2', NULL)
            """, docId, docId);
    }

    @Test
    void backfill_populatesMissingEmbeddings() {
        when(embeddingService.embed("chunk one")).thenReturn(unitVector(0));
        when(embeddingService.embed("chunk two")).thenReturn(unitVector(1));

        int updated = chunkEmbeddingService.backfillMissingEmbeddings(100);
        assertThat(updated).isGreaterThanOrEqualTo(2);

        Integer stillNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ? AND embedding IS NULL",
                Integer.class,
                docId
        );
        assertThat(stillNull).isEqualTo(0);
    }

    private static float[] unitVector(int index) {
        float[] v = new float[1536];
        v[index] = 1.0f;
        return v;
    }
}
