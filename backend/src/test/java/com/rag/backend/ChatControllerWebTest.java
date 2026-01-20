package com.rag.backend;

import com.rag.backend.controller.ChatController;
import com.rag.backend.dto.RagAnswer;
import com.rag.backend.rag.RagChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerWebTest {

    @Autowired MockMvc mvc;

    @MockBean RagChatService ragChatService;

    // These are constructor deps of ChatController too; WebMvcTest needs them mocked
    @MockBean com.rag.backend.ai.EmbeddingService embeddingService;
    @MockBean com.rag.backend.repo.ChunkRepo chunkRepo;

    @Test
    void postChat_delegatesToRagChatService_andReturnsJson() throws Exception {
        RagAnswer ra = new RagAnswer("Hello from RAG", List.of());
        when(ragChatService.answerQuestion(eq("Hi"))).thenReturn(ra);

        MediaType contentType = Objects.requireNonNull(MediaType.APPLICATION_JSON, "Content type must not be null");
        mvc.perform(post("/api/chat")
                        .contentType(contentType)
                        .content("""
                            {"message":"Hi"}
                        """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(contentType))
                .andExpect(jsonPath("$.answer").value("Hello from RAG"))
                .andExpect(jsonPath("$.citations").isArray());
    }
}
