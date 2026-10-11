package com.health.service;

import com.health.common.config.TrafficProtectionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GlobalTrafficLimiterTest {
    @Test void instancesUseSameBucketWithConfiguredRateAndCapacity() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TrafficProtectionProperties properties = new TrafficProtectionProperties();
        properties.setRequestsPerSecond(12);
        properties.setBurstCapacity(4);
        properties.setRedisKey("test:shared:api");
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)))
                .thenReturn(0L, 84L);
        assertEquals(0, new GlobalTrafficLimiter(redis, properties).acquire());
        assertEquals(84, new GlobalTrafficLimiter(redis, properties).acquire());
        verify(redis, times(2)).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("test:shared:api")), eq("12"), eq("4"));
    }

    @Test void missingOrInvalidResultAndRedisOutageNeverFailOpen() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GlobalTrafficLimiter limiter = new GlobalTrafficLimiter(redis, new TrafficProtectionProperties());
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)))
                .thenReturn(null, -1L).thenThrow(new DataAccessResourceFailureException("test offline"));
        assertThrows(IllegalStateException.class, limiter::acquire);
        assertThrows(IllegalStateException.class, limiter::acquire);
        assertThrows(DataAccessResourceFailureException.class, limiter::acquire);
    }
}
