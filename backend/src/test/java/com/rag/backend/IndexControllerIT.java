package com.rag.backend;

import com.rag.backend.retrieval.ChunkEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IndexControllerIT {

    @Autowired TestRestTemplate rest;

    @MockBean ChunkEmbeddingService chunkEmbeddingService;

    @Test
    void reindex_endpoint_returns_200() {
        when(chunkEmbeddingService.backfillMissingEmbeddings(anyInt())).thenReturn(123);

        ResponseEntity<String> res =
                rest.postForEntity("/api/reindex", null, String.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).contains("Re-indexed");
    }
}
