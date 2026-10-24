package com.health.service.impl;

import com.health.ai.*;
import com.health.common.config.RuntimeSettings;
import com.health.memory.*;
import com.health.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMemoryIntegrationTest {
    final AiClient ai=mock(AiClient.class);
    final LongTermMemoryService longTerm=mock(LongTermMemoryService.class);
    final ChatContextService contexts=mock(ChatContextService.class);
    final AppointmentConversationService appointments=mock(AppointmentConversationService.class);
    final LongTermMemoryService.Snapshot snapshot=new LongTermMemoryService.Snapshot(1L,true,0,1,List.of());
    ChatServiceImpl chat;
    @BeforeEach void setup() {
        var health=mock(HealthRecordService.class);var sports=mock(SportRecordService.class);
        when(health.getHealthRecordsByUserId(1L,1,5)).thenReturn(Map.of("records",List.of()));
        when(sports.getSportRecordsByUserId(1L,1,5)).thenReturn(Map.of("records",List.of()));
        when(longTerm.beginTurn(eq(1L),anyString())).thenReturn(snapshot);
        when(longTerm.context(snapshot)).thenReturn("偏好清淡饮食");
        when(contexts.prepare(eq(1L),anyString(),eq(snapshot),anyMap())).thenAnswer(inv->new ChatContextService.Turn(
                new AiRequest(1L,"deepseek","private","model","chat",null,inv.getArgument(3),List.of(),inv.getArgument(1)),"turn",0));
        chat=new ChatServiceImpl(ai,appointments,health,sports,contexts,new RuntimeSettings(),longTerm);
    }
    @AfterEach void close() { chat.close(); }
    @Test void regularCompletionArchivesOriginalReplyBeforeQueuingLongTermMemory() {
        when(ai.complete(any())).thenAnswer(inv->{assertEquals("偏好清淡饮食",inv.<AiRequest>getArgument(0).variables().get("longTermMemories"));return "建议";});
        assertEquals("建议",chat.chat("吃什么",1L).getReply());
        var order=inOrder(contexts,longTerm);order.verify(contexts).complete(any(),eq("建议"));
        order.verify(longTerm).completeTurn(eq(snapshot),eq("吃什么"),any());
    }
    @Test void failedGenerationKeepsArchivedInputAndNeverWritesAssistantCompletion() {
        when(ai.complete(any())).thenThrow(new AiServiceException("不可用"));
        assertEquals("不可用",chat.chat("问题",1L).getReply());
        verify(contexts).prepare(eq(1L),eq("问题"),eq(snapshot),anyMap());
        verify(contexts,never()).complete(any(),anyString());verify(longTerm,never()).completeTurn(any(),anyString(),any());
    }
    @Test void streamingPreparationRunsOffRequestThreadAndCompletionPersists() {
        doAnswer(inv->{inv.<ChatService.StreamCallback>getArgument(1).onComplete("完整回答");return null;}).when(ai).stream(any(),any());
        var callback=mock(ChatService.StreamCallback.class);chat.chatStream("问题",1L,callback);
        verify(callback,timeout(2000)).onComplete("完整回答");verify(contexts).complete(any(),eq("完整回答"));
        verify(longTerm).completeTurn(eq(snapshot),eq("问题"),any());
    }
    @Test void partialStreamErrorNeverArchivesIncompleteAssistantReply() {
        doAnswer(inv->{var cb=inv.<ChatService.StreamCallback>getArgument(1);cb.onToken("部分");cb.onError(new RuntimeException());return null;}).when(ai).stream(any(),any());
        var callback=mock(ChatService.StreamCallback.class);chat.chatStream("问题",1L,callback);
        verify(callback,timeout(2000)).onError(any());verify(contexts,never()).complete(any(),anyString());
    }
    @Test void appointmentsAndEntitlementRemainOutsideBackgroundCompaction() {
        when(appointments.handleMessage("预约",1L)).thenReturn("请提供预约信息");
        assertEquals("请提供预约信息",chat.chat("预约",1L).getReply());verifyNoInteractions(contexts,longTerm);
        doThrow(new AiServiceException("无权限")).when(ai).requireAccess(1L);
        assertThrows(AiServiceException.class,()->chat.chatStream("问题",1L,mock(ChatService.StreamCallback.class)));
    }
}
