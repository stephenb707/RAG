package com.rag.backend;

import com.rag.backend.ai.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RetrieveEndpointIT {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @MockBean EmbeddingService embeddingService;

    private long repoId;
    private long docId;

    @BeforeEach
    void setup() throws Exception {
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

        insertChunk("A best", unitVector(0));
        insertChunk("B close", scaledUnitVector(0, 0.8f));
        insertChunk("C far", scaledUnitVector(0, -1.0f));
    }

    @Test
    void retrieve_returnsTopMatches() {
        when(embeddingService.embed("what is this?")).thenReturn(unitVector(0));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> req = new HttpEntity<>(
                Map.of("question", "what is this?"),
                headers
        );

        ResponseEntity<String> res =
                rest.postForEntity("/api/retrieve", req, String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        // keep assertions flexible: just ensure ordering appears in payload
        String body = res.getBody();
        assertThat(body).isNotNull();
        if (body != null) {
            assertThat(body).contains("A best");
            assertThat(body).contains("B close");
            assertThat(body).contains("C far");

            // Optional: if you want strict ordering:
            assertThat(body.indexOf("A best")).isLessThan(body.indexOf("B close"));
            assertThat(body.indexOf("B close")).isLessThan(body.indexOf("C far"));
        }
    }

    private void insertChunk(String content, float[] embedding) {
        PGobject vec = new PGobject();
        try {
            vec.setType("vector");
            vec.setValue(vectorLiteral(embedding));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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
