package com.health.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeSettingsTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RuntimeSettings.class)
    static class SettingsConfiguration {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SettingsConfiguration.class);

    @Test
    void customPoliciesBindAndThreadPoolUsesThem() {
        runner.withPropertyValues(
                "health.runtime.chat-memory.max-messages=4", "health.runtime.chat-memory.ttl=2h",
                "health.runtime.async.core-pool-size=2", "health.runtime.async.max-pool-size=3",
                "health.runtime.async.queue-capacity=11", "health.runtime.async.keep-alive=20s",
                "health.runtime.async.shutdown-wait=3s",
                "health.runtime.login.max-failures=8", "health.runtime.login.lock-duration=30m"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            RuntimeSettings settings = context.getBean(RuntimeSettings.class);
            assertEquals(4, settings.getChatMemory().getMaxMessages());
            assertEquals(Duration.ofHours(2), settings.getChatMemory().getTtl());
            assertEquals(8, settings.getLogin().getMaxFailures());
            assertEquals(Duration.ofMinutes(30), settings.getLogin().getLockDuration());
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig(settings).getAsyncExecutor();
            try {
                assertEquals(2, executor.getCorePoolSize());
                assertEquals(3, executor.getMaxPoolSize());
                assertEquals(11, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
                assertEquals(20, executor.getKeepAliveSeconds());
            } finally { executor.shutdown(); }
        });
    }

    @Test
    void invalidPoliciesFailDuringStartup() {
        for (String bad : new String[]{
                "health.runtime.chat-memory.max-messages=0", "health.runtime.chat-memory.ttl=0s",
                "health.runtime.async.queue-capacity=-1", "health.runtime.async.keep-alive=500ms",
                "health.runtime.login.max-failures=0", "health.runtime.login.lock-duration=-1s"}) {
            runner.withPropertyValues(bad).run(context -> assertNotNull(context.getStartupFailure(), bad));
        }
        runner.withPropertyValues("health.runtime.async.core-pool-size=8", "health.runtime.async.max-pool-size=2")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
