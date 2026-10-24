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
    void redisMemoryAtomicallySavesWholeContextAndTtlWithoutMessageCountTruncation() {
        RuntimeSettings settings = new RuntimeSettings();
        settings.getChatMemory().setMaxMessages(2);
        settings.getChatMemory().setTtl(Duration.ofHours(2));
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class)))
                .thenAnswer(inv -> {
                    assertEquals(List.of("chat:memory:1"),inv.getArgument(1));
                    assertEquals(Duration.ofHours(2).toMillis(),inv.<Long>getArgument(2).longValue());
                    assertTrue(inv.getArgument(3).toString().contains("old"));
                    assertTrue(inv.getArgument(4).toString().contains("recent"));
                    assertTrue(inv.getArgument(5).toString().contains("latest"));
                    return 1L;
                });
        new RedisChatMemoryStore(redis, settings).updateMessages(1L,
                List.of(UserMessage.from("old"), UserMessage.from("recent"), UserMessage.from("latest")));
        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
        verify(redis,never()).delete(anyString());
    }

}
