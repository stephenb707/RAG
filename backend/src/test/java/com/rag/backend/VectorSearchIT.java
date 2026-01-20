package com.rag.backend;

import com.rag.backend.entity.ChunkEntity;
import com.rag.backend.repo.ChunkRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VectorSearchIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired ChunkRepo chunkRepo;

    private long repoId;
    private long docId;

    @BeforeEach
    void setupRepoAndDoc() {
        jdbc.execute("TRUNCATE TABLE chunks, documents, repositories RESTART IDENTITY CASCADE");

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
    }

    @Test
    void searchSimilar_returnsClosestFirst() throws Exception {
        // Query vector: [1,0,0,0,...] (length 1536)
        float[] q = unitVector(0);

        // chunk A: exactly matches query => distance 0 (best)
        insertChunk("A best", unitVector(0));
        // chunk B: close => distance small
        insertChunk("B close", scaledUnitVector(0, 0.8f));
        // chunk C: far => distance larger
        insertChunk("C far", scaledUnitVector(0, -1.0f));

        List<ChunkEntity> results = chunkRepo.searchTopK(vectorLiteral(q), 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getContent()).isEqualTo("A best");
        assertThat(results.get(1).getContent()).isEqualTo("B close");
        assertThat(results.get(2).getContent()).isEqualTo("C far");
    }

    private void insertChunk(String content, float[] embedding) throws Exception {
        PGobject vec = new PGobject();
        vec.setType("vector");
        vec.setValue(vectorLiteral(embedding)); // "[0,0,1,...]"

        Integer nextIndex = jdbc.queryForObject(
                "SELECT COALESCE(MAX(chunk_index), -1) + 1 FROM chunks WHERE document_id = ?",
                Integer.class,
                docId
        );

        jdbc.update("""
                INSERT INTO chunks(document_id, chunk_index, start_line, end_line, content, content_hash, embedding)
                VALUES (?, ?, 1, 1, ?, 'hash', ?)
                """,
                docId, nextIndex, content, vec
        );
    }

    private static float[] unitVector(int index) {
        float[] v = new float[1536];
        v[index] = 1.0f;
        return v;
    }

    private static float[] scaledUnitVector(int index, float scale) {
        float[] v = new float[1536];
        v[index] = scale;
        return v;
    }

    private static String vectorLiteral(float[] v) {
        // pgvector literal format: "[1,0,0,...]"
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            // keep it simple/compact
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
