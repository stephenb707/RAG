package com.rag.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatControllerIT {

    @Autowired
    private TestRestTemplate rest;

    // Request/response DTOs for the test
    record ChatRequest(String message) {}
    record ChatResponse(String reply) {}

    @Test
    void chat_returnsDummyReply() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ChatRequest body = new ChatRequest("hello");
        HttpEntity<ChatRequest> req = new HttpEntity<>(body, headers);

        ResponseEntity<ChatResponse> resp =
                rest.postForEntity("/api/chat", req, ChatResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ChatResponse responseBody = resp.getBody();
        assertThat(responseBody).isNotNull();
        if (responseBody != null) {
            assertThat(responseBody.reply()).contains("hello");
        }
    }
}
