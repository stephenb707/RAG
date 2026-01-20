package com.rag.backend.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAIChatClient {

    private final OpenAIClient client;

    public OpenAIChatClient(@Value("${OPENAI_API_KEY}") String apiKey) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public String chat(String system, String user) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                // Use a model enum that definitely exists in the SDK README
                .model(ChatModel.GPT_4O)
                .addSystemMessage(system)
                .addUserMessage(user)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);

        return completion.choices()
                .get(0)
                .message()
                .content()
                .orElse("");
    }
}
