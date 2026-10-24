package com.health.ai;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Map;
@Component
public class ModelFactory {
    public static final String EMBEDDING_MODEL="text-embedding-v4";
    public static final String EMBEDDING_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1";
    private final Duration timeout;
    private final int outputTokens;
    @Value("${ai.context.summary-model:}") private String summaryModel="";
    @Value("${ai.context.summary-timeout-seconds:25}") private int summaryTimeout=25;
    public ModelFactory(@Value("${ai.timeout-seconds:60}") int seconds,
                        @Value("${ai.context.output-tokens:8192}") int outputTokens) {
        timeout=Duration.ofSeconds(seconds); this.outputTokens=outputTokens;
    }
    @jakarta.annotation.PostConstruct public void validateSummarySettings() {
        if(summaryTimeout<1 || summaryTimeout>25 || !summaryModel.isBlank() && !summaryModel.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,99}"))
            throw new IllegalArgumentException("摘要模型配置无效");
    }
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
                .modelName(EMBEDDING_MODEL).baseUrl(EMBEDDING_BASE_URL)
                .timeout(timeout).maxRetries(0).logRequests(false).logResponses(false).build();
    }
    public dev.langchain4j.model.embedding.EmbeddingModel memoryEmbedding(String key) {
        return dev.langchain4j.model.openai.OpenAiEmbeddingModel.builder().apiKey(key)
                .modelName(EMBEDDING_MODEL).baseUrl(EMBEDDING_BASE_URL)
                .timeout(Duration.ofSeconds(4)).maxRetries(0).logRequests(false).logResponses(false).build();
    }
    public ChatModel chat(AiRequest r) {
        return OpenAiChatModel.builder().apiKey(r.apiKey()).modelName(r.model()).baseUrl(baseUrl(r))
                .temperature(0.3).maxTokens("chat".equals(r.task())?outputTokens:null).timeout(timeout).maxRetries(0).logRequests(false).logResponses(false).build();
    }
    public String summaryModelName(AiRequest r) { return summaryModel.isBlank()?r.model():summaryModel; }
    public ChatModel summary(AiRequest r,int maxTokens) {
        String name=summaryModelName(r);
        if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,99}") || summaryTimeout<1 || summaryTimeout>25)
            throw new IllegalArgumentException("摘要模型配置无效");
        return OpenAiChatModel.builder().apiKey(r.apiKey()).modelName(name).baseUrl(baseUrl(r))
                .temperature(0.0).maxTokens(maxTokens).timeout(Duration.ofSeconds(summaryTimeout)).maxRetries(0)
                .responseFormat("json_object").customParameters("deepseek".equals(r.provider())?Map.of("thinking",Map.of("type","disabled")):Map.of())
                .logRequests(false).logResponses(false).build();
    }
    public StreamingChatModel stream(AiRequest r) {
        return OpenAiStreamingChatModel.builder().apiKey(r.apiKey()).modelName(r.model()).baseUrl(baseUrl(r))
                .temperature(0.3).maxTokens("chat".equals(r.task())?outputTokens:null).timeout(timeout).returnThinking(true).logRequests(false).logResponses(false).build();
    }
}
