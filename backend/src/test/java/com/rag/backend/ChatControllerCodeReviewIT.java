package com.rag.backend;

import com.rag.backend.dto.ChatRequest;
import com.rag.backend.dto.RagAnswer;
import com.rag.backend.rag.RagChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatControllerCodeReviewIT {

    @Autowired TestRestTemplate rest;

    @MockBean RagChatService ragChatService;

    @Test
    void code_review_returns_200() {
        when(ragChatService.answerWithMode(eq("Hi"), eq(RagChatService.Mode.CODE_REVIEW)))
                .thenReturn(new RagAnswer("code review answer", List.of()));

        ResponseEntity<RagAnswer> res =
                rest.postForEntity("/api/chat/code-review", new ChatRequest("Hi"), RagAnswer.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        RagAnswer responseBody = res.getBody();
        assertThat(responseBody).isNotNull();
        if (responseBody != null) {
            assertThat(responseBody.answer()).contains("code review");
        }
    }
}
