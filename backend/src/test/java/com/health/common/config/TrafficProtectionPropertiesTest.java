package com.health.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class TrafficProtectionPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TrafficProtectionProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test void defaultsAndCustomLimitsBind() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            TrafficProtectionProperties properties = context.getBean(TrafficProtectionProperties.class);
            assertTrue(properties.isEnabled());
            assertEquals(50, properties.getRequestsPerSecond());
            assertEquals(50, properties.getBurstCapacity());
            assertEquals(16, properties.getMaxConcurrentRequests());
        });
        runner.withPropertyValues("health.traffic-protection.enabled=false",
                "health.traffic-protection.requests-per-second=8", "health.traffic-protection.burst-capacity=3",
                "health.traffic-protection.max-concurrent-requests=2", "health.traffic-protection.redis-key=test:api")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    TrafficProtectionProperties properties = context.getBean(TrafficProtectionProperties.class);
                    assertFalse(properties.isEnabled());
                    assertEquals(8, properties.getRequestsPerSecond());
                    assertEquals(3, properties.getBurstCapacity());
                    assertEquals(2, properties.getMaxConcurrentRequests());
                    assertEquals("test:api", properties.getRedisKey());
                });
    }

    @Test void invalidLimitsFailAtStartup() {
        for (String property : new String[]{"requests-per-second=0", "burst-capacity=-1",
                "max-concurrent-requests=0", "redis-key="}) {
            runner.withPropertyValues("health.traffic-protection." + property)
                    .run(context -> assertNotNull(context.getStartupFailure(), property));
        }
    }
}
