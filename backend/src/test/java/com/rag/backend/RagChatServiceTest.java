package com.rag.backend;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.ai.OpenAIChatClient;
import com.rag.backend.dto.RagAnswer;
import com.rag.backend.entity.ChunkEntity;
import com.rag.backend.entity.DocumentEntity;
import com.rag.backend.rag.RagChatService;
import com.rag.backend.repo.ChunkRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagChatServiceTest {

    @Test
    void answerQuestion_embeds_retrieves_buildsPrompt_callsLLM_returnsAnswerAndCitations() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ChunkRepo chunkRepo = mock(ChunkRepo.class);
        OpenAIChatClient chatClient = mock(OpenAIChatClient.class);

        RagChatService service = new RagChatService(embeddingService, chunkRepo, chatClient);

        // Arrange
        float[] q = new float[1536];
        q[0] = 1.0f;
        when(embeddingService.embed("How do I run tests?")).thenReturn(q);

        ChunkEntity c1 = chunk("backend/src/main/java/X.java", 10, 30, "Snippet one");
        ChunkEntity c2 = chunk("backend/src/main/java/Y.java", 5, 12, "Snippet two");

        when(chunkRepo.searchTopK(anyString(), eq(5))).thenReturn(List.of(c1, c2));

        when(chatClient.chat(anyString(), anyString()))
                .thenReturn("Use the Maven wrapper: ./mvnw test");

        // Act
        RagAnswer answer = service.answerQuestion("How do I run tests?");

        // Assert basic output (adjust getters if your RagAnswer names differ)
        assertThat(answer).isNotNull();
        assertThat(answer.answer()).contains("Maven wrapper");
        assertThat(answer.citations()).hasSize(2);

        // Assert prompt content
        ArgumentCaptor<String> sysCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCap = ArgumentCaptor.forClass(String.class);
        verify(chatClient).chat(sysCap.capture(), userCap.capture());

        String system = sysCap.getValue();
        String user = userCap.getValue();

        assertThat(system)
        .containsIgnoringCase("provided context")
        .containsIgnoringCase("cite");


        assertThat(user).contains("How do I run tests?");
        assertThat(user).contains("backend/src/main/java/X.java");
        assertThat(user).contains("10-30");
        assertThat(user).contains("Snippet one");
        assertThat(user).contains("backend/src/main/java/Y.java");
        assertThat(user).contains("5-12");
        assertThat(user).contains("Snippet two");

        // Ensure it passed a pgvector literal string (not float[])
        ArgumentCaptor<String> vecCap = ArgumentCaptor.forClass(String.class);
        verify(chunkRepo).searchTopK(vecCap.capture(), eq(5));
        assertThat(vecCap.getValue()).startsWith("[");
        assertThat(vecCap.getValue()).endsWith("]");
    }

    @Test
    void answerQuestion_whenNoChunks_returnsEmptyCitations_stillCallsLLM() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ChunkRepo chunkRepo = mock(ChunkRepo.class);
        OpenAIChatClient chatClient = mock(OpenAIChatClient.class);

        RagChatService service = new RagChatService(embeddingService, chunkRepo, chatClient);

        float[] q = new float[1536];
        when(embeddingService.embed("What is pgvector?")).thenReturn(q);
        when(chunkRepo.searchTopK(anyString(), eq(5))).thenReturn(List.of());

        when(chatClient.chat(anyString(), anyString()))
                .thenReturn("I don't have enough context from the repository to answer.");

        RagAnswer answer = service.answerQuestion("What is pgvector?");

        assertThat(answer.citations()).isEmpty();
        assertThat(answer.answer()).containsIgnoringCase("don't have enough context");
        verify(chatClient).chat(anyString(), anyString());
    }

    private static ChunkEntity chunk(String filePath, int startLine, int endLine, String content) {
        DocumentEntity d = new DocumentEntity();
        d.setFilePath(filePath);

        ChunkEntity c = new ChunkEntity();
        c.setDocument(d);
        c.setStartLine(startLine);
        c.setEndLine(endLine);
        c.setContent(content);
        return c;
    }
}
