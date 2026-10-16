package com.health.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.*;
import com.health.domain.dto.AppointmentExtractionContext;
import com.health.domain.vo.HealthRecordVO;
import com.health.service.*;
import com.health.common.utils.SecurityUtil;
import com.health.common.config.RuntimeSettings;
import com.health.memory.LongTermMemoryService;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class PromptIntegrationTest {
    @Test void chatSendsAuthorizedHealthContextToExtractedService() {
        AiClient ai=mock(AiClient.class); var health=mock(HealthRecordService.class); var sports=mock(SportRecordService.class);
        HealthRecordVO record=new HealthRecordVO(); record.setHeartRate(75);
        when(health.getHealthRecordsByUserId(1L,1,5)).thenReturn(Map.of("records",List.of(record)));
        when(sports.getSportRecordsByUserId(1L,1,5)).thenReturn(Map.of("records",List.of()));
        when(ai.complete(any())).thenReturn("健康建议");
        var memories=mock(LongTermMemoryService.class);
        when(memories.context(any())).thenReturn("暂无长期记忆");
        var chat=new ChatServiceImpl(ai,mock(AppointmentConversationService.class),health,sports,mock(RedisChatMemoryStore.class),new RuntimeSettings(),memories);
        assertEquals("健康建议",chat.chat("你好",1L).getReply());
        ArgumentCaptor<Map<String,String>> context=ArgumentCaptor.forClass(Map.class);
        verify(ai).prepare(eq(1L),eq("chat"),context.capture(),anyList(),eq("你好"));
        assertTrue(context.getValue().get("healthRecords").contains("心率: 75"));
        assertThrows(NullPointerException.class,()->chat.chat("你好",null));
    }
    @Test void appointmentUsesCallerKeyAndLeavesPlaceholdersAsUserData() {
        var ai=mock(AiClient.class); var security=mock(SecurityUtil.class); when(security.getCurrentUserId()).thenReturn(1L);
        when(ai.complete(any())).thenReturn("{\"appointmentIntent\":true,\"department\":\"内科\"}");
        var extractor=new AiAppointmentInformationExtractor(ai,new ObjectMapper(),security,
                new DepartmentCatalog(new ClassPathResource("catalogs/departments.yml")));
        var context=new AppointmentExtractionContext(); context.setPatientName("{{departments}} $1");
        assertTrue(extractor.extract("预约内科",context).getAppointmentIntent());
        ArgumentCaptor<Map<String,String>> captured=ArgumentCaptor.forClass(Map.class);
        verify(ai).prepare(eq(1L),eq("appointment"),captured.capture(),anyList(),eq("预约内科"));
        assertTrue(captured.getValue().get("context").contains("{{departments}} $1"));
    }
}
