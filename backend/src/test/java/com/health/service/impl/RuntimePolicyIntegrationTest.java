package com.health.service.impl;

import com.health.common.config.RuntimeSettings;
import com.health.domain.entity.User;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimePolicyIntegrationTest {
    @Test
    void redisMemoryKeepsConfiguredWindowAndTtl() {
        RuntimeSettings settings = new RuntimeSettings();
        settings.getChatMemory().setMaxMessages(2);
        settings.getChatMemory().setTtl(Duration.ofHours(2));
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ListOperations<String, Object> lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        new RedisChatMemoryStore(redis, settings).updateMessages(1L,
                List.of(UserMessage.from("old"), UserMessage.from("recent"), UserMessage.from("latest")));
        verify(lists, times(2)).rightPush(eq("chat:memory:1"), any());
        verify(lists, never()).rightPush(anyString(), argThat(value -> value.toString().contains("old")));
        verify(redis).expire("chat:memory:1", Duration.ofHours(2));
    }

}
