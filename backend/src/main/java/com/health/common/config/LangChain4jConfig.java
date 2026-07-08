package com.health.common.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j AI 配置类
 * 通过 OpenAI 兼容接口接入大语言模型，支持 OpenAI / 通义千问 / DeepSeek 等
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${langchain4j.openai.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.model-name:gpt-4o}")
    private String modelName;

    @Value("${langchain4j.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${langchain4j.openai.temperature:0.3}")
    private Double temperature;

    @Value("${langchain4j.openai.timeout:60}")
    private Integer timeout;

    @Value("${langchain4j.openai.embedding-model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Value("${langchain4j.openai.embedding-base-url:${langchain4j.openai.base-url:https://api.openai.com/v1}}")
    private String embeddingBaseUrl;

    @Bean
    public ChatModel chatModel() {
        log.info("初始化 LangChain4j ChatModel: model={}, baseUrl={}", modelName, baseUrl);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 LangChain4j EmbeddingModel: model={}, baseUrl={}", embeddingModelName, embeddingBaseUrl);
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .baseUrl(embeddingBaseUrl)
                .timeout(Duration.ofSeconds(timeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
