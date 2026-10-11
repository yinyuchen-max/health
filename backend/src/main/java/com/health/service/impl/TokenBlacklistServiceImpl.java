package com.health.service.impl;

import com.health.service.TokenBlacklistService;
import com.health.common.utils.JwtUtil;
import com.health.common.security.TokenDigests;
import com.health.common.exception.AuthStoreUnavailableException;
import com.health.service.AuthSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务实现
 * 使用 Redis 存储已注销的 JWT Token
 */
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistServiceImpl.class);

    /** Redis key 前缀 */
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    /** 用户 token 集合前缀 */
    private static final String USER_TOKENS_PREFIX = "auth:user-tokens:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final AuthSessionService sessions;

    public TokenBlacklistServiceImpl(RedisTemplate<String, Object> redisTemplate, JwtUtil jwtUtil, AuthSessionService sessions) {
        this.sessions = sessions;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void addToBlacklist(String token, Date expiration) {
        try {
            String key = BLACKLIST_PREFIX + TokenDigests.sha256(token);
            long ttlMillis = expiration.getTime() - System.currentTimeMillis();

            if (ttlMillis <= 0) {
                // Token 已过期，无需加入黑名单
                return;
            }

            redisTemplate.opsForValue().set(key, "1", ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Token 已加入黑名单，TTL: {} ms", ttlMillis);
        } catch (Exception e) {
            throw new AuthStoreUnavailableException();
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        try {
            String key = BLACKLIST_PREFIX + TokenDigests.sha256(token);
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            throw new AuthStoreUnavailableException();
        }
    }

    @Override
    public void blacklistAllTokensForUser(String username) {
        sessions.revokeAll(username);
        try {
            String userTokensKey = USER_TOKENS_PREFIX + username;
            // 标记该用户需要强制重新登录
            // 覆盖仍有效的旧 JWT 的最长剩余寿命，与签发配置共用同一个来源。
            long ttlMillis = jwtUtil.getExpirationMillis();
            redisTemplate.opsForValue().set(userTokensKey, System.currentTimeMillis(), ttlMillis, TimeUnit.MILLISECONDS);
            log.info("已为用户 {} 设置强制下线标记", username);
        } catch (Exception e) {
            throw new AuthStoreUnavailableException();
        }
    }

    /**
     * 检查用户的 token 是否在强制下线标记之后签发
     * @param username 用户名
     * @param tokenIssuedAt token 签发时间
     * @return true 表示 token 已失效（需要重新登录）
     */
    public boolean isTokenInvalidated(String username, Date tokenIssuedAt) {
        try {
            String userTokensKey = USER_TOKENS_PREFIX + username;
            Object invalidatedAt = redisTemplate.opsForValue().get(userTokensKey);

            if (invalidatedAt instanceof Number) {
                long invalidatedTime = ((Number) invalidatedAt).longValue();
                return tokenIssuedAt.getTime() < invalidatedTime;
            }
            return false;
        } catch (Exception e) {
            throw new AuthStoreUnavailableException();
        }
    }
}
