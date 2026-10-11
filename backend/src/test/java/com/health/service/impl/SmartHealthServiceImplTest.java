package com.health.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.*;
import com.health.domain.entity.*;
import com.health.mapper.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class SmartHealthServiceImplTest {
    @Test void shouldSendOnlyHealthContextAndFallBackOnRemoteFailure() {
        var health=mock(HealthRecordMapper.class); var sport=mock(SportRecordMapper.class); var users=mock(UserMapper.class);
        var ai=mock(AiClient.class); var service=new SmartHealthServiceImpl(health,sport,users,ai,new ObjectMapper());
        User user=new User(); user.setId(1L); user.setPassword("must-never-leave-core"); user.setAge(30);
        when(users.selectById(1L)).thenReturn(user);
        HealthRecord record=new HealthRecord(); record.setHeartRate(75);
        when(health.selectList(any())).thenReturn(List.of(record)); when(sport.selectList(any())).thenReturn(List.of());
        when(ai.payer(1L)).thenReturn(1L); when(ai.cacheScope(1L)).thenReturn("scope");
        when(ai.complete(any())).thenThrow(new AiServiceException("unavailable"));
        var result=service.generateOverview(1L);
        assertEquals("RULES",result.getAnalysisSource()); assertEquals(4,result.getRiskAssessments().size());
        ArgumentCaptor<Map<String,String>> capture=ArgumentCaptor.forClass(Map.class);
        verify(ai).prepare(eq(1L),eq("report"),capture.capture(),eq(List.of()),isNull());
        assertTrue(capture.getValue().get("latestMetrics").contains("75"));
        assertFalse(capture.getValue().toString().contains("must-never-leave-core"));
    }
}
