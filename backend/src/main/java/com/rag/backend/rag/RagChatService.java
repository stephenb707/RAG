package com.rag.backend.rag;

import com.rag.backend.ai.EmbeddingService;
import com.rag.backend.ai.OpenAIChatClient;
import com.rag.backend.dto.Citation;
import com.rag.backend.dto.RagAnswer;
import com.rag.backend.entity.ChunkEntity;
import com.rag.backend.repo.ChunkRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagChatService {

    private final EmbeddingService embeddingService;
    private final ChunkRepo chunkRepo;
    private final OpenAIChatClient chatClient;

    public RagChatService(
            EmbeddingService embeddingService,
            ChunkRepo chunkRepo,
            OpenAIChatClient chatClient
    ) {
        this.embeddingService = embeddingService;
        this.chunkRepo = chunkRepo;
        this.chatClient = chatClient;
    }

    public RagAnswer answerQuestion(String question) {

        // 1) Embed question
        float[] questionEmbedding = embeddingService.embed(question);

        // 2) Retrieve top 5 relevant chunks
        List<ChunkEntity> chunks =
                chunkRepo.searchTopK(toPgVectorLiteral(questionEmbedding), 5);

        // 3) Build context block for LLM
        String context = buildContext(chunks);

        // 4) Build full prompt
        String systemPrompt = """
            You are a senior software engineer.
            Only answer using the provided context.
            You MUST cite file paths and line ranges in your answer.
            """;

        String userPrompt = """
            QUESTION:
            %s

            CONTEXT:
            %s
            """.formatted(question, context);

        // 5) Call OpenAI
        String answer = chatClient.chat(systemPrompt, userPrompt);

        List<Citation> citations = chunks.stream()
            .map(c -> new Citation(
                c.getDocument().getFilePath(),
                c.getStartLine(),
                c.getEndLine(),
                c.getContent()
            ))
            .toList();

        return new RagAnswer(answer, citations);
    }

    private String buildContext(List<ChunkEntity> chunks) {
        return chunks.stream()
                .map(c ->
                        """
                        ---
                        File: %s
                        Lines: %d-%d
                        Snippet:
                        %s
                        """.formatted(
                                c.getDocument().getFilePath(),
                                c.getStartLine(),
                                c.getEndLine(),
                                c.getContent()
                        )
                )
                .collect(Collectors.joining("\n"));
    }

    private String toPgVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
