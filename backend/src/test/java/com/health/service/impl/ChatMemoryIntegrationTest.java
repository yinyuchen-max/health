package com.health.service.impl;

import com.health.ai.*;
import com.health.common.config.RuntimeSettings;
import com.health.memory.LongTermMemoryService;
import com.health.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMemoryIntegrationTest {
    private final AiClient ai = mock(AiClient.class);
    private final LongTermMemoryService longTerm = mock(LongTermMemoryService.class);
    private final AppointmentConversationService appointments = mock(AppointmentConversationService.class);
    private final RedisChatMemoryStore recent = mock(RedisChatMemoryStore.class);
    private final LongTermMemoryService.Snapshot snapshot = new LongTermMemoryService.Snapshot(1L,true,0,1,List.of());
    private ChatServiceImpl chat;

    @BeforeEach void setup() {
        var health = mock(HealthRecordService.class); var sports = mock(SportRecordService.class);
        when(health.getHealthRecordsByUserId(1L,1,5)).thenReturn(Map.of("records",List.of()));
        when(sports.getSportRecordsByUserId(1L,1,5)).thenReturn(Map.of("records",List.of()));
        when(longTerm.beginTurn(1L)).thenReturn(snapshot);
        when(longTerm.context(snapshot)).thenReturn("偏好清淡饮食");
        when(ai.prepare(anyLong(),anyString(),anyMap(),anyList(),anyString())).thenAnswer(inv ->
                new AiRequest(inv.getArgument(0),"deepseek","private","model",inv.getArgument(1),null,
                        inv.getArgument(2),inv.getArgument(3),inv.getArgument(4)));
        chat = new ChatServiceImpl(ai,appointments,health,sports,recent,new RuntimeSettings(),longTerm);
    }
    @Test void normalChatInjectsMemoryAndOnlySuccessfulReplyTriggersPersistence() {
        when(ai.complete(any())).thenAnswer(inv -> {
            AiRequest request=inv.getArgument(0);
            assertEquals("偏好清淡饮食",request.variables().get("longTermMemories"));
            return "建议";
        });
        assertEquals("建议",chat.chat("吃什么",1L).getReply());
        verify(longTerm).completeTurn(eq(snapshot),eq("吃什么"),any());
        clearInvocations(longTerm);
        doThrow(new AiServiceException("不可用")).when(ai).complete(any());
        assertEquals("不可用",chat.chat("失败请求",1L).getReply());
        verify(longTerm,never()).completeTurn(any(),anyString(),any());
    }
    @Test void streamingCompletionPersistsButStreamErrorDoesNot() {
        doAnswer(inv -> { inv.<ChatService.StreamCallback>getArgument(1).onComplete("完整回答"); return null; })
                .when(ai).stream(any(),any());
        var callback=mock(ChatService.StreamCallback.class);
        chat.chatStream("吃什么",1L,callback);
        verify(longTerm).completeTurn(eq(snapshot),eq("吃什么"),any());
        verify(callback).onComplete("完整回答");
        clearInvocations(longTerm);
        doAnswer(inv -> { inv.<ChatService.StreamCallback>getArgument(1).onError(new RuntimeException()); return null; })
                .when(ai).stream(any(),any());
        chat.chatStream("失败请求",1L,callback);
        verify(longTerm,never()).completeTurn(any(),anyString(),any());
        verify(callback).onError(any());
    }
    @Test void appointmentDetailsAreNotAutomaticallyAddedToHealthMemory() {
        when(appointments.handleMessage("预约",1L)).thenReturn("请提供预约信息");
        assertEquals("请提供预约信息",chat.chat("预约",1L).getReply());
        verifyNoInteractions(longTerm);
    }
}
