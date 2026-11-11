package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.common.config.RuntimeSettings;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RedisChatMemoryStoreTest {
    @Test void newFormatRoundTripsQuotesNewlinesUnitsAndLiteralTemplateText() throws Exception {
        RedisTemplate<String,Object> redis=mock(RedisTemplate.class);
        ListOperations<String,Object> lists=mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        var stored=new ArrayList<Object>();
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenAnswer(inv -> {
            for(int i=3;i<inv.getArguments().length;i++) stored.add(inv.getArgument(i));
            return 1L;
        });
        var memory=new RedisChatMemoryStore(redis,new RuntimeSettings());
        String evidence="我说：\"不是我\"。\n父亲用药 0.25 mg，{{healthRecords}} $1 \\";
        var original=List.<ChatMessage>of(UserMessage.from(evidence),AiMessage.from("请确认\"药名\""));
        memory.updateMessages(9L,original);
        assertEquals(2,stored.size());
        assertEquals(evidence,new ObjectMapper().readTree(stored.get(0).toString()).path("content").asText());
        when(lists.range("chat:memory:9",0,-1)).thenReturn(stored);
        assertEquals(original,memory.getMessages(9L));
    }

    @Test void legacyMessagesAreReadableAndCorruptOrUnavailableHistoryDoesNotSilentlyDisappear() {
        RedisTemplate<String,Object> redis=mock(RedisTemplate.class);
        ListOperations<String,Object> lists=mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        var memory=new RedisChatMemoryStore(redis,new RuntimeSettings());
        when(lists.range("chat:memory:1",0,-1)).thenReturn(List.of(
                "{\"type\":\"USER\",\"content\":\"UserMessage { text = \\\"我没有过敏\\\" }\"}"));
        assertEquals(List.of(UserMessage.from("我没有过敏")),memory.getMessages(1L));
        when(lists.range("chat:memory:1",0,-1)).thenReturn(List.of("invalid-json"));
        assertThrows(IllegalStateException.class,()->memory.getMessages(1L));
        when(lists.range("chat:memory:1",0,-1)).thenThrow(new IllegalStateException("Redis down"));
        assertThrows(IllegalStateException.class,()->memory.getMessages(1L));
    }
}
