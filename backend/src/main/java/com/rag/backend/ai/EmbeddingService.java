package com.rag.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

@Service
public class EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    public float[] embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }

        String url = "https://api.openai.com/v1/embeddings";
        String key = Objects.requireNonNull(apiKey, "API key must not be null");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(key);

        EmbeddingRequest payload = new EmbeddingRequest("text-embedding-3-small", text);

        HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(payload, headers);

        HttpMethod method = Objects.requireNonNull(HttpMethod.POST, "HTTP method must not be null");
        ResponseEntity<EmbeddingResponse> resp =
                restTemplate.exchange(url, method, entity, EmbeddingResponse.class);

        EmbeddingResponse body = resp.getBody();
        if (body == null || body.data() == null || body.data().isEmpty()) {
            throw new IllegalStateException("No embedding returned from OpenAI");
        }

        List<Double> vector = body.data().get(0).embedding();
        float[] out = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) out[i] = vector.get(i).floatValue();
        return out;
    }

    // --- DTOs for JSON mapping ---

    public record EmbeddingRequest(String model, String input) {}

    public record EmbeddingResponse(List<EmbeddingData> data) {}

    public record EmbeddingData(List<Double> embedding) {}
}
