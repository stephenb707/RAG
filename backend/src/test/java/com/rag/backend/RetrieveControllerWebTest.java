package com.rag.backend;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.controller.ChatController;
import com.rag.backend.repo.ChunkRepo;
import com.rag.backend.rag.RagChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Objects;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class RetrieveControllerWebTest {

    @Autowired MockMvc mvc;

    @MockBean EmbeddingService embeddingService;
    @MockBean ChunkRepo chunkRepo;
    @MockBean RagChatService ragChatService;

    @Test
    void postRetrieve_whenQuestionMissing_returns400() throws Exception {
        MediaType contentType = Objects.requireNonNull(MediaType.APPLICATION_JSON, "Content type must not be null");
        mvc.perform(post("/api/retrieve")
                        .contentType(contentType)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(embeddingService, chunkRepo);
    }

    @Test
    void postRetrieve_whenQuestionBlank_returns400() throws Exception {
        MediaType contentType = Objects.requireNonNull(MediaType.APPLICATION_JSON, "Content type must not be null");
        mvc.perform(post("/api/retrieve")
                        .contentType(contentType)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(embeddingService, chunkRepo);
    }
}
