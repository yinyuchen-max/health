package com.health.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AiClient;
import com.health.ai.AiRequest;
import com.health.common.exception.BusinessException;
import com.health.service.impl.RedisChatMemoryStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LongTermMemoryServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiClient ai = mock(AiClient.class);
    private final RedisChatMemoryStore recent = mock(RedisChatMemoryStore.class);
    private final Deque<Runnable> jobs = new ArrayDeque<>();
    private UserMemoryRepository repository;
    private TransactionTemplate transactions;
    private JdbcTemplate jdbc;
    private LongTermMemoryService service;

    @BeforeEach void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:memory-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO sys_user(id) VALUES(1),(2)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__add_user_long_term_memory.sql")).execute(ds);
        repository = new UserMemoryRepository(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = new LongTermMemoryService(repository, transactions, ai, new MemoryExtraction(json), json, jobs::add, recent);
        when(ai.prepare(anyLong(), anyString(), anyMap(), anyList(), anyString())).thenAnswer(inv ->
                new AiRequest(inv.getArgument(0), "deepseek", "private", "model", inv.getArgument(1), null,
                        inv.getArgument(2), inv.getArgument(3), inv.getArgument(4)));
    }

    private MemoryExtraction.Candidate fact(String operation, String key, String content, boolean explicit) {
        String category = key.split("\\.")[0];
        return new MemoryExtraction.Candidate(operation, category, key, content, content,
                "ongoing", null, explicit, category.equals("family") ? "family" : "self", "asserted");
    }
    private void extract(LongTermMemoryService.Snapshot snapshot, String message, MemoryExtraction.Candidate... facts) throws Exception {
        when(ai.complete(any())).thenReturn(json.writeValueAsString(new MemoryExtraction.Result(List.of(facts))));
        service.completeTurn(snapshot, message, () -> {});
        jobs.removeFirst().run();
    }

    @Test void successfulTurnExtractsOnWorkerUsesOwnersKeyAndSurvivesNewServiceInstance() throws Exception {
        var turn = service.beginTurn(1L);
        AtomicBoolean saved = new AtomicBoolean();
        when(ai.complete(any())).thenReturn(json.writeValueAsString(new MemoryExtraction.Result(
                List.of(fact("ADD", "diet.preference", "我喜欢清淡饮食", false)))));
        service.completeTurn(turn, "我喜欢清淡饮食", () -> saved.set(true));
        assertTrue(saved.get()); verifyNoInteractions(ai);
        jobs.removeFirst().run();
        verify(ai).prepare(eq(1L), eq("memory-extract"), anyMap(), eq(List.of()), eq("我喜欢清淡饮食"));
        var restarted = new LongTermMemoryService(repository, transactions, ai, new MemoryExtraction(json), json, jobs::add, recent);
        assertTrue(restarted.context(restarted.beginTurn(1L)).contains("清淡饮食"));
        assertTrue(restarted.view(2L).memories().isEmpty());
    }

    @Test void deduplicatesAndExplicitGoalUpdateReplacesValue() throws Exception {
        extract(service.beginTurn(1L), "目标65公斤", fact("ADD", "goal.target_weight", "目标65公斤", false));
        extract(service.beginTurn(1L), "目标65公斤", fact("NOOP", "goal.target_weight", "目标65公斤", false));
        assertEquals(1, service.view(1L).memories().size());
        extract(service.beginTurn(1L), "目标改为60公斤", fact("UPDATE", "goal.target_weight", "目标改为60公斤", true));
        assertEquals("目标改为60公斤", service.view(1L).memories().get(0).content());
    }

    @Test void healthContradictionRequiresConfirmationAndIsExcludedFromPrompt() throws Exception {
        extract(service.beginTurn(1L), "我对花生过敏", fact("ADD", "health.allergy.peanut", "我对花生过敏", false));
        extract(service.beginTurn(1L), "我对花生不过敏", fact("UPDATE", "health.allergy.peanut", "我对花生不过敏", true));
        var entry = service.view(1L).memories().get(0);
        assertEquals("CONFLICT", entry.status()); assertEquals("我对花生过敏", entry.content());
        assertEquals("我对花生不过敏", entry.pendingContent());
        assertFalse(service.context(service.beginTurn(1L)).contains("花生"));
        service.edit(1L, entry.id(), "我对花生不过敏");
        assertEquals("ACTIVE", service.view(1L).memories().get(0).status());
        assertTrue(service.context(service.beginTurn(1L)).contains("花生不过敏"));
    }

    @Test void olderModelResponseCannotOverwriteLaterTurn() {
        var earlier = service.beginTurn(1L); var later = service.beginTurn(1L);
        service.apply(later, List.of(fact("ADD", "goal.target_weight", "60公斤", false)));
        service.apply(earlier, List.of(fact("UPDATE", "goal.target_weight", "65公斤", true)));
        assertEquals("60公斤", service.view(1L).memories().get(0).content());
    }

    @Test void deletingWhileExtractionRunsCannotResurrectDataOrRecentHistory() throws Exception {
        extract(service.beginTurn(1L), "清淡", fact("ADD", "diet.preference", "清淡", false));
        var running = service.beginTurn(1L);
        Long id = service.view(1L).memories().get(0).id();
        // Simulates a remote model response arriving after deletion committed.
        service.delete(1L, id);
        service.apply(running, List.of(fact("UPDATE", "diet.preference", "重口味", true)));
        AtomicBoolean historySaved = new AtomicBoolean();
        service.completeTurn(running, "重口味", () -> historySaved.set(true));
        assertFalse(historySaved.get()); assertTrue(service.view(1L).memories().isEmpty());
        verify(recent).clearStrict(1L);
    }

    @Test void queuedJobsAreInvalidatedEvenAfterDisableThenReenable() {
        var turn = service.beginTurn(1L);
        service.completeTurn(turn, "清淡", () -> {});
        service.setEnabled(1L, false);
        service.setEnabled(1L, true);
        jobs.removeFirst().run();
        verifyNoInteractions(ai);
        assertTrue(service.view(1L).memories().isEmpty());
    }

    @Test void disabledMemoryIsNeitherInjectedNorExtractedButRecentConversationStillWorks() throws Exception {
        extract(service.beginTurn(1L), "清淡", fact("ADD", "diet.preference", "清淡", false));
        service.setEnabled(1L, false);
        var disabled = service.beginTurn(1L);
        AtomicBoolean saved = new AtomicBoolean();
        service.completeTurn(disabled, "你好", () -> saved.set(true));
        assertTrue(saved.get()); assertTrue(jobs.isEmpty());
        assertFalse(service.context(disabled).contains("清淡"));
        assertEquals(1, service.view(1L).memories().size());
    }

    @Test void editsAndClearAreOwnerScopedAndInvalidateOldJobs() throws Exception {
        extract(service.beginTurn(1L), "清淡", fact("ADD", "diet.preference", "清淡", false));
        Long id = service.view(1L).memories().get(0).id();
        assertThrows(BusinessException.class, () -> service.delete(2L, id));
        assertThrows(BusinessException.class, () -> service.edit(2L, id, "伪造"));
        service.clear(2L); assertEquals(1, service.view(1L).memories().size());
        var stale = service.beginTurn(1L);
        service.edit(1L, id, "素食");
        service.apply(stale, List.of(fact("UPDATE", "diet.preference", "重口味", true)));
        assertEquals("素食", service.view(1L).memories().get(0).content());
        service.clear(1L);
        service.apply(stale, List.of(fact("ADD", "diet.preference", "清淡", false)));
        assertTrue(service.view(1L).memories().isEmpty());
    }

    @Test void redisFailureRollsBackForgetRatherThanReportingFalseSuccess() throws Exception {
        extract(service.beginTurn(1L), "清淡", fact("ADD", "diet.preference", "清淡", false));
        doThrow(new IllegalStateException("offline")).when(recent).clearStrict(1L);
        assertThrows(IllegalStateException.class, () -> service.clear(1L));
        assertEquals(1, service.view(1L).memories().size());
    }

    @Test void expiredFactsAreNotUsedAndTemporaryFactsGetExpiry() {
        var temporary = new MemoryExtraction.Candidate("ADD", "health", "health.symptom.headache", "今天头疼", "今天头疼",
                "temporary", 1, false, "self", "asserted");
        service.apply(service.beginTurn(1L), List.of(temporary));
        assertNotNull(service.view(1L).memories().get(0).expiresAt());
        jdbc.update("UPDATE user_memory SET expires_at=?", LocalDateTime.now().minusDays(1));
        assertTrue(service.view(1L).memories().isEmpty());
        assertFalse(service.context(service.beginTurn(1L)).contains("头疼"));
    }

    @Test void boundedMemoryDoesNotEvictImportantFactsToFitNewOnes() {
        var turn = service.beginTurn(1L);
        for (int i=0; i<50; i++) repository.insert(1L, "diet.item_"+i, "diet", "旧事实"+i, "原文", turn.turn(), null);
        service.apply(service.beginTurn(1L), List.of(fact("ADD", "exercise.habit", "游泳", false)));
        assertEquals(50, service.view(1L).memories().size());
    }

    @Test void malformedModelOutputOrExecutorOverloadCannotFailChat() {
        when(ai.complete(any())).thenReturn("not json");
        service.completeTurn(service.beginTurn(1L), "你好", () -> {});
        assertDoesNotThrow(() -> jobs.removeFirst().run());
        assertTrue(service.view(1L).memories().isEmpty());
        var saturated = new LongTermMemoryService(repository, transactions, ai, new MemoryExtraction(json), json,
                command -> { throw new RejectedExecutionException(); }, recent);
        assertDoesNotThrow(() -> saturated.completeTurn(saturated.beginTurn(1L), "你好", () -> {}));
    }

    @Test void explicitNaturalLanguageForgetClearsBothStores() throws Exception {
        extract(service.beginTurn(1L), "清淡", fact("ADD", "diet.preference", "清淡", false));
        var deletion = new MemoryExtraction.Candidate("DELETE", "diet", "diet.preference", null, "忘掉我的饮食偏好",
                null, null, true, "self", "asserted");
        extract(service.beginTurn(1L), "请忘掉我的饮食偏好", deletion);
        assertTrue(service.view(1L).memories().isEmpty());
        verify(recent).clearStrict(1L);
    }

    @Test void databaseLockOrdersDeletionAfterAnInFlightHistoryWrite() throws Exception {
        var snapshot=service.beginTurn(1L);
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var cleared=new AtomicBoolean();
        var workers=Executors.newFixedThreadPool(2);
        doAnswer(inv -> { cleared.set(true); return null; }).when(recent).clearStrict(1L);
        try {
            var completion=workers.submit(() -> service.completeTurn(snapshot,"清淡",() -> {
                entered.countDown();
                try { assertTrue(release.await(5,TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                assertFalse(cleared.get(),"Deletion cannot clear Redis before the locked reply write finishes");
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var deletion=workers.submit(() -> service.clear(1L));
            release.countDown();
            completion.get(5,TimeUnit.SECONDS); deletion.get(5,TimeUnit.SECONDS);
            assertTrue(cleared.get());
            jobs.removeFirst().run();
            verifyNoInteractions(ai);
        } finally { release.countDown(); workers.shutdownNow(); }
    }
}
