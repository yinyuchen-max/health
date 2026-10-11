package com.health.service.impl;

import com.health.common.utils.JwtUtil;
import com.health.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenBlacklistServiceImplTest {
    @Test
    void forceLogoutTtlFollowsLongAndShortJwtLifetimeInMilliseconds() {
        for (long ttl : new long[]{Duration.ofHours(48).toMillis(), Duration.ofMinutes(15).toMillis()}) {
            RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
            ValueOperations<String, Object> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            JwtUtil jwt = new JwtUtil();
            ReflectionTestUtils.setField(jwt, "expiration", ttl);
            TokenBlacklistServiceImpl service = new TokenBlacklistServiceImpl(redis, jwt, mock(AuthSessionService.class));
            service.blacklistAllTokensForUser("test-user");
            verify(values).set(eq("auth:user-tokens:test-user"), anyLong(), eq(ttl), eq(TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void markerRejectsEarlierTokensButAllowsLaterSignIns() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        long cutoff = System.currentTimeMillis();
        when(values.get("auth:user-tokens:test-user")).thenReturn(cutoff);
        TokenBlacklistServiceImpl service = new TokenBlacklistServiceImpl(redis, new JwtUtil(), mock(AuthSessionService.class));
        assertTrue(service.isTokenInvalidated("test-user", new Date(cutoff - 1000)));
        assertFalse(service.isTokenInvalidated("test-user", new Date(cutoff + 1000)));
    }
    @Test
    void blacklistStoresDigestAndFailsClosedOnRedisErrors() {
        RedisTemplate<String,Object> redis = mock(RedisTemplate.class);
        ValueOperations<String,Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var service = new TokenBlacklistServiceImpl(redis, new JwtUtil(), mock(AuthSessionService.class));
        String token = "test-only-token";
        service.addToBlacklist(token, new Date(System.currentTimeMillis()+60000));
        verify(values).set(eq("auth:blacklist:"+com.health.common.security.TokenDigests.sha256(token)), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        when(redis.hasKey(anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("test failure"));
        assertThrows(com.health.common.exception.AuthStoreUnavailableException.class, () -> service.isBlacklisted(token));
    }
}
