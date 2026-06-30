package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.domain.dto.SmartHealthOverviewDTO;
import com.health.domain.entity.User;
import com.health.mapper.HealthRecordMapper;
import com.health.mapper.SportRecordMapper;
import com.health.mapper.UserMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartHealthServiceImplTest {

    @Test
    void shouldFallBackToRuleEngineWhenAiGenerationExceedsTimeout() {
        HealthRecordMapper healthRecordMapper = mock(HealthRecordMapper.class);
        SportRecordMapper sportRecordMapper = mock(SportRecordMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        ChatModel chatModel = mock(ChatModel.class);

        SmartHealthServiceImpl service = new SmartHealthServiceImpl(
                healthRecordMapper,
                sportRecordMapper,
                userMapper,
                chatModel,
                new ObjectMapper()
        );

        User user = new User();
        user.setId(1L);
        user.setAge(30);
        user.setGender(1);
        user.setHeight(175.0);
        user.setWeight(70.0);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(healthRecordMapper.selectList(any())).thenReturn(List.of());
        when(sportRecordMapper.selectList(any())).thenReturn(List.of());
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("timeout of 15000ms exceeded"));

        SmartHealthOverviewDTO overview = assertTimeoutPreemptively(
                Duration.ofMillis(500),
                () -> service.generateOverview(1L)
        );

        assertNotNull(overview);
        assertEquals(1L, overview.getUserId());
        assertEquals(4, overview.getRiskAssessments().size());
        verify(chatModel).chat(anyString());
    }
}
