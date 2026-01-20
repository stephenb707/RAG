package com.rag.backend;

import com.rag.backend.dto.RagAnswer;
import com.rag.backend.rag.RagChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatControllerIT {

    @Autowired private TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    // Mock the service so this test never calls OpenAI / embeddings.
    @MockBean RagChatService ragChatService;

    @BeforeEach
    void resetDb() {
        jdbc.execute("TRUNCATE TABLE chunks, documents, repositories RESTART IDENTITY CASCADE");
    }

    record ChatRequest(String message) {}

    @Test
    void chat_returnsDummyReply() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ChatRequest body = new ChatRequest("hello");
        HttpEntity<ChatRequest> req = new HttpEntity<>(body, headers);

        when(ragChatService.answerQuestion("hello"))
                .thenReturn(new RagAnswer("hello from test", List.of()));

        ResponseEntity<RagAnswer> resp =
                rest.postForEntity("/api/chat", req, RagAnswer.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        RagAnswer responseBody = resp.getBody();
        assertThat(responseBody).isNotNull();
        if (responseBody != null) {
            assertThat(responseBody.answer()).contains("hello");
            assertThat(responseBody.citations()).isEmpty();
        }
    }
}
