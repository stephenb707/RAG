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
    public enum Mode {
        DEFAULT,
        EXPLAIN_ARCHITECTURE,
        CODE_REVIEW
    }    

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
        return answerWithMode(question, Mode.DEFAULT);
    }

    public RagAnswer answerWithMode(String question, Mode mode) {

        float[] questionEmbedding = embeddingService.embed(question);
    
        List<ChunkEntity> chunks =
                chunkRepo.searchTopK(toPgVectorLiteral(questionEmbedding), 5);
    
        String context = buildContext(chunks);
    
        String systemPrompt = switch (mode) {
            case EXPLAIN_ARCHITECTURE ->
                    """
                    You are a senior software architect.
                    Explain the high-level architecture of this system
                    using ONLY the provided context.
                    Cite files and line ranges.
                    """;
    
            case CODE_REVIEW ->
                    """
                    You are a senior code reviewer.
                    Provide constructive suggestions for improvement
                    using ONLY the provided context.
                    Cite files and line ranges.
                    """;
    
            default ->
                    """
                    You are a senior software engineer.
                    Only answer using the provided context.
                    You MUST cite file paths and line ranges in your answer.
                    """;
        };
    
        String userPrompt = """
                QUESTION:
                %s
    
                CONTEXT:
                %s
                """.formatted(question, context);
    
        String answer = chatClient.chat(systemPrompt, userPrompt);
    
        return new RagAnswer(answer, toCitations(chunks));
    }

    private List<Citation> toCitations(List<ChunkEntity> chunks) {
        return chunks.stream()
                .map(c -> new Citation(
                        c.getDocument().getFilePath(),
                        c.getStartLine(),
                        c.getEndLine(),
                        c.getContent()
                ))
                .toList();
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
