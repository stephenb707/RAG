package com.rag.backend;

import com.rag.backend.repo.ChunkRepo;
import com.rag.backend.repo.DocumentRepo;
import com.rag.backend.repo.RepositoryRepo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IndexingIT {

    @Autowired private TestRestTemplate rest;
    @Autowired private RepositoryRepo repositoryRepo;
    @Autowired private DocumentRepo documentRepo;
    @Autowired private ChunkRepo chunkRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetDb() {
        // Add other tables here if you have them (tenants, users, etc.)
        jdbc.execute("TRUNCATE TABLE chunks, documents, repositories RESTART IDENTITY CASCADE");
    }

    record IndexRequest(String repoName, String rootPath) {}
    record IndexResponse(
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

    @Test
    void index_createsRepositoryDocumentsAndChunks() throws Exception {
        // Arrange: create a tiny “repo” on disk (inside container when running dockerized tests)
        Path root = Files.createTempDirectory("sample-repo");
        Files.writeString(root.resolve("README.md"), "# Hello\nThis is a test\n");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(
                root.resolve("src").resolve("Demo.java"),
                "public class Demo {\n" +
                "  public static void main(String[] args) {\n" +
                "    System.out.println(\"hi\");\n" +
                "  }\n" +
                "}\n"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        IndexRequest body = new IndexRequest("sample-repo", root.toString());
        HttpEntity<IndexRequest> req = new HttpEntity<>(body, headers);

        // Act
        ResponseEntity<IndexResponse> resp =
                rest.postForEntity("/api/index", req, IndexResponse.class);

        // Assert: endpoint worked
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        IndexResponse responseBody = resp.getBody();
        assertThat(responseBody).isNotNull();
        if (responseBody != null) {
            assertThat(responseBody.filesScanned()).isGreaterThanOrEqualTo(2);
            assertThat(responseBody.filesIndexed()).isGreaterThanOrEqualTo(2);
            assertThat(responseBody.documentsUpserted()).isGreaterThanOrEqualTo(2);
            assertThat(responseBody.chunksCreated()).isGreaterThan(0);
            // Note: chunksEmbedded might be 0 if embedding service is not available in test
            assertThat(responseBody.chunksEmbedded()).isGreaterThanOrEqualTo(0);
            assertThat(responseBody.elapsedMsTotal()).isGreaterThanOrEqualTo(0);
        }

        // Assert: DB has rows
        assertThat(repositoryRepo.count()).isGreaterThanOrEqualTo(1);
        assertThat(documentRepo.count()).isGreaterThanOrEqualTo(2);
        assertThat(chunkRepo.count()).isGreaterThan(0);
    }
}
