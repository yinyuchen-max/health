package com.health.service.impl;

import com.health.common.config.AsyncConfig;
import com.health.service.HealthKnowledgeRagService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthKnowledgeRagStartupTest {

    @Test
    void startupListenerShouldRegisterAndWarmUpOnAsyncExecutor() throws Exception {
        HealthKnowledgeRedisRepository repository = mock(HealthKnowledgeRedisRepository.class);
        CountDownLatch built = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        when(repository.needsRebuild(anyString())).thenReturn(true);
        when(repository.hasActiveStore()).thenReturn(true);
        doAnswer(invocation -> {
            worker.set(Thread.currentThread());
            built.countDown();
            return null;
        }).when(repository).buildAndActivate(anyList(), anyString());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class));
            context.registerBean(HealthKnowledgeRedisRepository.class, () -> repository);
            context.register(AsyncConfig.class, HealthKnowledgeRagServiceImpl.class);

            // Refresh previously failed while registering the implementation-only listener.
            assertDoesNotThrow(context::refresh);
            assertNotNull(context.getBean(HealthKnowledgeRagService.class));
            verifyNoInteractions(repository);

            context.publishEvent(new ApplicationReadyEvent(
                    new SpringApplication(HealthKnowledgeRagStartupTest.class),
                    new String[0], context, Duration.ZERO));

            assertTrue(built.await(5, TimeUnit.SECONDS), "Startup event should build the index");
            assertNotSame(Thread.currentThread(), worker.get());
            assertTrue(worker.get().getName().startsWith("health-async-"));
            verify(repository).buildAndActivate(argThat(segments -> !segments.isEmpty()), anyString());
        }
    }
}
