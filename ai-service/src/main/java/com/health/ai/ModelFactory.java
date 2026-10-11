package com.health.ai;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component
public class ModelFactory {
    private final Duration timeout;
    public ModelFactory(@Value("${ai.timeout-seconds:60}") int seconds) { timeout=Duration.ofSeconds(seconds); }
    // The user selects a provider, never an arbitrary URL. Keys cannot be redirected to an attacker host.
    private String baseUrl(AiRequest request) {
        return switch(request.provider()) {
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "dashscope" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            default -> throw new IllegalArgumentException("服务商不受支持");
        };
    }
    public dev.langchain4j.model.embedding.EmbeddingModel embedding(String key) {
        return dev.langchain4j.model.openai.OpenAiEmbeddingModel.builder().apiKey(key)
                .modelName("text-embedding-v4").baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .timeout(timeout).maxRetries(0).logRequests(false).logResponses(false).build();
    }
    public ChatModel chat(AiRequest r) {
        return OpenAiChatModel.builder().apiKey(r.apiKey()).modelName(r.model()).baseUrl(baseUrl(r))
                .temperature(0.3).timeout(timeout).maxRetries(0).logRequests(false).logResponses(false).build();
    }
    public StreamingChatModel stream(AiRequest r) {
        return OpenAiStreamingChatModel.builder().apiKey(r.apiKey()).modelName(r.model()).baseUrl(baseUrl(r))
                .temperature(0.3).timeout(timeout).returnThinking(true).logRequests(false).logResponses(false).build();
    }
}
