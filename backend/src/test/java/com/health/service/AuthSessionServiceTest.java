package com.health.service;

import com.health.common.exception.AuthStoreUnavailableException;
import com.health.common.exception.UnauthorizedException;
import com.health.common.security.TokenDigests;
import com.health.common.utils.JwtUtil;
import com.health.domain.entity.User;
import com.health.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthSessionServiceTest {
    StringRedisTemplate redis;
    UserMapper users;
    User user;
    AuthSessionService service;
    JwtUtil jwt;
    @BeforeEach void setup() {
        redis = mock(StringRedisTemplate.class); users = mock(UserMapper.class);
        user = new User(); user.setId(1L); user.setUsername("alice"); user.setRole("user"); user.setStatus(1); user.setPassword("test-only-password-hash");
        when(users.selectOne(any())).thenReturn(user);
        jwt = new JwtUtil(); ReflectionTestUtils.setField(jwt, "secret", "test-only-secret-with-at-least-32-bytes");
        ReflectionTestUtils.setField(jwt, "expiration", 900000L);
        service = new AuthSessionService(redis, jwt, users);
        ReflectionTestUtils.setField(service, "refreshExpiration", Duration.ofDays(7));
    }
    @Test void issuanceStoresOnlyDigestsAndBrowserResponseHidesRefreshCredential() {
        AtomicReference<List<String>> keys = new AtomicReference<>();
        AtomicReference<Object[]> arguments = new AtomicReference<>();
        doAnswer(invocation -> {
            keys.set(invocation.getArgument(1));
            arguments.set(java.util.Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length));
            return "0";
        }).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        var tokens = service.issue(user, AuthSessionService.WEB);
        assertNull(tokens.forBrowser().refreshToken());
        assertFalse(tokens.toString().contains(tokens.token()));
        assertEquals(900, tokens.expiresIn());
        assertEquals(TokenDigests.sha256(tokens.refreshToken()), arguments.get()[2]);
        assertFalse(java.util.Arrays.asList(arguments.get()).contains(tokens.refreshToken()));
        String sid = jwt.parseAccessToken(tokens.token()).get("sid", String.class);
        assertTrue(keys.get().contains("auth:session:" + sid));
    }
    @Test void missingSessionRejectsEvenCorrectlySignedJwtAndDatabaseRoleWins() {
        String token = jwt.generateToken("alice", "admin", TokenDigests.randomToken(), 900000);
        doReturn(List.of()).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThrows(UnauthorizedException.class, () -> service.validateAccess(jwt.parseAccessToken(token)));
        String fingerprint = TokenDigests.sha256(user.getId()+":"+user.getPassword()+":"+user.getStatus()+":"+user.getRole());
        doReturn(List.of(fingerprint)).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertEquals("user", service.validateAccess(jwt.parseAccessToken(token)));
        user.setPassword("changed-hash");
        assertThrows(UnauthorizedException.class, () -> service.validateAccess(jwt.parseAccessToken(token)));
    }
    @Test void authStoreFailureDoesNotAcceptToken() {
        String token = jwt.generateToken("alice", "user", TokenDigests.randomToken(), 900000);
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("test unavailable"))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThrows(AuthStoreUnavailableException.class, () -> service.validateAccess(jwt.parseAccessToken(token)));
    }
    @Test void refreshRejectsWrongClientAndDisabledUserBeforeRotation() {
        HashOperations<String,Object,Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        String refresh = TokenDigests.randomToken()+"."+TokenDigests.randomToken();
        when(hashes.entries(anyString())).thenReturn(Map.of("username","alice","client","web"));
        assertThrows(UnauthorizedException.class, () -> service.refresh(refresh,"native"));
        user.setStatus(0);
        assertThrows(UnauthorizedException.class, () -> service.refresh(refresh,"web"));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
    @Test void forceLogoutAdvancesPersistentEpochRatherThanTimeCutoff() {
        ValueOperations<String,String> values = mock(ValueOperations.class); when(redis.opsForValue()).thenReturn(values);
        service.revokeAll("alice");
        verify(values).increment("auth:epoch:"+TokenDigests.sha256("alice"));
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }
}
