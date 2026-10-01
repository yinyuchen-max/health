package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.domain.dto.SmartHealthOverviewDTO;
import com.health.domain.entity.HealthRecord;
import com.health.domain.entity.HistoryRecord;
import com.health.domain.entity.SportRecord;
import com.health.domain.entity.User;
import com.health.mapper.HealthRecordMapper;
import com.health.mapper.HistoryRecordMapper;
import com.health.mapper.SportRecordMapper;
import com.health.mapper.UserMapper;
import com.health.service.HealthKnowledgeRagService;
import com.health.support.RedisMemoryFixture;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmartHealthCacheTest {
    private final RedisMemoryFixture cache = new RedisMemoryFixture();
    private final HealthRecordMapper healthMapper = mock(HealthRecordMapper.class);
    private final SportRecordMapper sportMapper = mock(SportRecordMapper.class);
    private final HistoryRecordMapper historyMapper = mock(HistoryRecordMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final ChatModel model = mock(ChatModel.class);
    private final HealthKnowledgeRagService rag = mock(HealthKnowledgeRagService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final User user = new User();
    private final List<HealthRecord> health = new ArrayList<>();
    private final List<SportRecord> sport = new ArrayList<>();
    private final List<HistoryRecord> history = new ArrayList<>();
    private SmartHealthServiceImpl service;

    private SmartHealthServiceImpl service() {
        SmartHealthServiceImpl result = new SmartHealthServiceImpl(healthMapper, sportMapper, userMapper, model, json, rag);
        ReflectionTestUtils.setField(result, "stringRedisTemplate", cache.redis);
        ReflectionTestUtils.setField(result, "historyRecordMapper", historyMapper);
        return result;
    }

    @BeforeEach
    void setUp() {
        user.setId(1L);
        user.setAge(30);
        user.setHeight(175.0);
        user.setWeight(70.0);
        user.setGender(1);
        HealthRecord record = new HealthRecord();
        record.setId(10L);
        record.setRecordDate("2025-01-01");
        record.setWeight(70.0);
        record.setBloodSugar(new BigDecimal("5.0"));
        health.add(record);
        SportRecord exercise = new SportRecord();
        exercise.setId(20L);
        exercise.setRecordDate("2025-01-01");
        exercise.setSportType("walking");
        exercise.setDuration(30);
        sport.add(exercise);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(healthMapper.selectList(any())).thenAnswer(i -> new ArrayList<>(health));
        when(sportMapper.selectList(any())).thenAnswer(i -> new ArrayList<>(sport));
        when(historyMapper.selectList(any())).thenAnswer(i -> new ArrayList<>(history));
        when(rag.retrieveRelevantKnowledge(any(), any(), any(), any(), any())).thenReturn(List.of("知识依据"));
        when(model.chat(anyString())).thenReturn("{\"overallStatus\":\"AI result\"}");
        service = service();
    }

    @Test
    void emptyRecordsShouldReturnNoticeWithoutModelsCacheOrHistoryLookup() {
        health.clear();
        sport.clear();
        // 即使填了个人体征、保留旧缓存和历史副本，也不能凭空生成报告。
        cache.data.put("smart:overview:v2:1", "old cached report");
        HistoryRecord oldHistory = new HistoryRecord();
        oldHistory.setContent("old health record");
        history.add(oldHistory);

        SmartHealthOverviewDTO result = service.generateOverview(1L);

        assertEquals("NO_DATA", result.getDataStatus());
        assertEquals(1L, result.getUserId());
        assertTrue(result.getMessage().contains("请先添加"));
        assertNull(result.getGeneratedAt());
        assertNull(result.getBmi());
        assertNull(result.getNutritionAdvice());
        assertNull(result.getExercisePlan());
        assertNull(result.getSleepInsight());
        assertNull(result.getStressInsight());
        assertTrue(result.getRiskAssessments().isEmpty());
        assertTrue(result.getQuickTips().isEmpty());
        verifyNoInteractions(model, rag, historyMapper, cache.redis);
    }

    @Test
    void deletingAllRecordsShouldHidePreviousReportAndNewRecordShouldEnableGeneration() {
        assertEquals("AVAILABLE", service.generateOverview(1L).getDataStatus());
        health.clear();
        sport.clear();
        assertEquals("NO_DATA", service.generateOverview(1L).getDataStatus());
        verify(model).chat(anyString());
        verify(rag).retrieveRelevantKnowledge(any(), any(), any(), any(), any());

        HealthRecord newRecord = new HealthRecord();
        newRecord.setId(99L);
        newRecord.setHeartRate(80);
        health.add(newRecord);
        SmartHealthOverviewDTO result = service.generateOverview(1L);
        assertEquals("AVAILABLE", result.getDataStatus());
        assertNull(result.getMessage());
        verify(model, times(2)).chat(anyString());
    }

    @Test
    void sportRecordAloneShouldAllowAnalysis() {
        health.clear();
        assertEquals("AVAILABLE", service.generateOverview(1L).getDataStatus());
        verify(model).chat(anyString());
    }

    @Test
    void unchangedDataShouldReuseOldReportAfterRestartWithoutTtlOrRagCalls() throws Exception {
        service.generateOverview(1L);
        String key = "smart:overview:v2:1";
        SmartHealthServiceImpl.CachedOverview entry = json.readValue(cache.data.get(key), SmartHealthServiceImpl.CachedOverview.class);
        entry.overview().setGeneratedAt("2020-01-01T00:00:00");
        cache.data.put(key, json.writeValueAsString(entry));
        SmartHealthOverviewDTO reused = service().generateOverview(1L);
        assertEquals("2020-01-01T00:00:00", reused.getGeneratedAt());
        assertEquals("AI result", reused.getOverallStatus());
        verify(model).chat(anyString());
        verify(rag).retrieveRelevantKnowledge(any(), any(), any(), any(), any());
        verify(cache.values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void addEditDeleteAndProfileChangesShouldRegenerateOncePerChange() {
        service.generateOverview(1L);
        List<Runnable> changes = List.of(
                () -> health.get(0).setBloodPressureSystolic(130.0),
                () -> health.get(0).setNotes("new note"),
                () -> sport.get(0).setCalories(123.0),
                () -> sport.get(0).setNotes("new sport note"),
                () -> user.setHeight(180.0),
                () -> user.setWeight(75.0),
                () -> user.setAge(31),
                () -> user.setGender(2),
                () -> { HealthRecord record = new HealthRecord(); record.setId(11L); health.add(record); },
                () -> health.remove(1),
                () -> sport.clear());
        int count = 1;
        for (Runnable change : changes) {
            change.run();
            service.generateOverview(1L);
            service.generateOverview(1L);
            verify(model, times(++count)).chat(anyString());
        }
        assertEquals(1, cache.data.size(), "Only the latest report per user should be stored");
    }

    @Test
    void historyContentChangesShouldInvalidateAndReachPrompt() {
        service.generateOverview(1L);
        HistoryRecord record = new HistoryRecord();
        record.setId(30L);
        record.setContent("history note one");
        record.setRecordDate(LocalDateTime.of(2025, 1, 1, 0, 0));
        history.add(record);
        service.generateOverview(1L);
        record.setContent("history note two");
        service.generateOverview(1L);
        service.generateOverview(1L);
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(model, times(3)).chat(prompts.capture());
        assertTrue(prompts.getValue().contains("history note two"));
        history.clear();
        service.generateOverview(1L);
        verify(model, times(4)).chat(anyString());
    }

    @Test
    void OrderingTechnicalTimestampsAndEquivalentNumbersShouldNotInvalidate() {
        HealthRecord second = new HealthRecord();
        second.setId(11L);
        health.add(second);
        service.generateOverview(1L);
        health.get(0).setBloodSugar(new BigDecimal("5.00"));
        health.get(0).setUpdateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setUsername("renamed");
        Collections.reverse(health);
        service.generateOverview(1L);
        verify(model).chat(anyString());
    }

    @Test
    void differentUsersShouldNotShareReport() {
        when(userMapper.selectById(2L)).thenAnswer(i -> {
            User another = new User(); another.setId(2L); return another;
        });
        service.generateOverview(1L);
        assertEquals(2L, service.generateOverview(2L).getUserId());
        verify(model, times(2)).chat(anyString());
        assertEquals(2, cache.data.size());
    }

    @Test
    void concurrentRequestsShouldGenerateOnlyOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(model.chat(anyString())).thenAnswer(i -> {
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return "{\"overallStatus\":\"AI result\"}";
        });
        CompletableFuture<SmartHealthOverviewDTO> first = CompletableFuture.supplyAsync(() -> service.generateOverview(1L));
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        CompletableFuture<SmartHealthOverviewDTO> second = CompletableFuture.supplyAsync(() -> service.generateOverview(1L));
        release.countDown();
        assertEquals(first.get(3, TimeUnit.SECONDS).getGeneratedAt(), second.get(3, TimeUnit.SECONDS).getGeneratedAt());
        verify(model).chat(anyString());
        verify(rag).retrieveRelevantKnowledge(any(), any(), any(), any(), any());
    }

    @Test
    void fallbackReportShouldAlsoBeReusedForUnchangedData() {
        when(model.chat(anyString())).thenThrow(new RuntimeException("unavailable"));
        SmartHealthOverviewDTO first = service.generateOverview(1L);
        assertEquals(first.getGeneratedAt(), service().generateOverview(1L).getGeneratedAt());
        verify(model).chat(anyString());
    }
}
