package com.health.service.impl;

import com.health.common.config.RagRedisProperties;
import com.health.support.RedisMemoryFixture;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthKnowledgeRedisRepositoryTest {
    private final RedisMemoryFixture cache = new RedisMemoryFixture();
    private final RagRedisProperties properties = new RagRedisProperties();
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final List<TextSegment> segments = List.of(TextSegment.from("运动健康知识"));

    private HealthKnowledgeRedisRepository repository() {
        return new HealthKnowledgeRedisRepository(properties, cache.redis, model);
    }

    private void stubEmbeddings() {
        when(model.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1, 0}))));
    }

    @Test
    void unchangedKnowledgeShouldSurviveRestartAndRemainSearchable() {
        stubEmbeddings();
        HealthKnowledgeRedisRepository first = repository();
        assertTrue(first.needsRebuild("source-a"));
        first.buildAndActivate(segments, "source-a");
        assertFalse(first.needsRebuild("source-a"));
        first.buildAndActivate(segments, "source-a");
        HealthKnowledgeRedisRepository restarted = repository();
        assertFalse(restarted.needsRebuild("source-a"));
        assertEquals(List.of("运动健康知识"), restarted.search(Embedding.from(new float[]{1, 0}), 6, 0.1));
        verify(model, times(1)).embedAll(anyList());
        assertTrue(cache.data.containsKey(properties.key("index-snapshot:v2")));
    }

    @Test
    void changedKnowledgeOrModelShouldRebuild() {
        stubEmbeddings();
        HealthKnowledgeRedisRepository repo = repository();
        repo.buildAndActivate(segments, "source-a");
        assertTrue(repo.needsRebuild("source-b"));
        assertFalse(repo.hasActiveStore());
        repo.buildAndActivate(segments, "source-b");
        assertFalse(repo.needsRebuild("source-b"));
        ReflectionTestUtils.setField(repo, "embeddingModelName", "different-model");
        assertTrue(repo.needsRebuild("source-b"));
        repo.buildAndActivate(segments, "source-b");
        verify(model, times(3)).embedAll(anyList());
    }

    @Test
    void lockContenderMustNotCallModelIndependently() {
        properties.setLockWait(Duration.ZERO);
        cache.data.put(properties.key("rebuild-lock"), "other-owner");
        HealthKnowledgeRedisRepository repo = repository();
        repo.buildAndActivate(segments, "source-a");
        assertFalse(repo.hasActiveStore());
        verifyNoInteractions(model);
        assertEquals("other-owner", cache.data.get(properties.key("rebuild-lock")));
    }

    @Test
    void failedBuildShouldPreservePreviousSnapshotAndReleaseLock() {
        stubEmbeddings();
        HealthKnowledgeRedisRepository repo = repository();
        repo.buildAndActivate(segments, "source-a");
        String saved = cache.data.get(properties.key("index-snapshot:v2"));
        when(model.embedAll(anyList())).thenThrow(new RuntimeException("embedding unavailable"));
        repo.buildAndActivate(segments, "source-b");
        assertEquals(saved, cache.data.get(properties.key("index-snapshot:v2")));
        assertFalse(repo.hasActiveStore());
        assertFalse(cache.data.containsKey(properties.key("rebuild-lock")));
        assertFalse(repository().needsRebuild("source-a"));
    }

    @Test
    void expiredLockMustNotOverwriteAnotherOwnersSnapshotOrLock() {
        when(model.embedAll(anyList())).thenAnswer(i -> {
            cache.data.put(properties.key("rebuild-lock"), "new-owner");
            cache.data.put(properties.key("index-snapshot:v2"), "newer-snapshot");
            return Response.from(List.of(Embedding.from(new float[]{1, 0})));
        });
        repository().buildAndActivate(segments, "source-a");
        assertEquals("newer-snapshot", cache.data.get(properties.key("index-snapshot:v2")));
        assertEquals("new-owner", cache.data.get(properties.key("rebuild-lock")));
    }

    @Test
    void corruptSnapshotShouldBeReplacedOnlyAfterSuccessfulBuild() {
        cache.data.put(properties.key("index-snapshot:v2"), "invalid-json");
        stubEmbeddings();
        HealthKnowledgeRedisRepository repo = repository();
        assertTrue(repo.needsRebuild("source-a"));
        repo.buildAndActivate(segments, "source-a");
        assertFalse(repository().needsRebuild("source-a"));
    }

    @Test
    void queryVectorsShouldPersistWithoutTtlAndInvalidateByContentAndModel() {
        when(model.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1, 0})));
        HealthKnowledgeRedisRepository repo = repository();
        repo.embedQuery("health query", 1L);
        assertArrayEquals(new float[]{1, 0}, repository().embedQuery("health query", 1L).vector());
        verify(model).embed(anyString());
        repo.embedQuery("changed query", 1L);
        ReflectionTestUtils.setField(repo, "embeddingModelName", "new-model");
        repo.embedQuery("changed query", 1L);
        verify(model, times(3)).embed(anyString());
        verify(cache.values, never()).set(anyString(), anyString(), any(Duration.class));
        assertEquals(1, cache.data.size());
    }
}
