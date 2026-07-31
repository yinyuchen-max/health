package com.health.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 分布式限流服务
 * <p>
 * 基于 Redis + Lua 脚本实现原子性计数限流。
 * 在时间窗口内统计请求次数，超过阈值则拒绝请求。
 * </p>
 */
@Slf4j
@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua 脚本：原子性地执行 INCR + EXPIRE
     * KEYS[1] = 限流 key
     * ARGV[1] = 时间窗口（秒）
     * 返回值：当前请求计数
     */
    private static final String LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local window = tonumber(ARGV[1]) " +
            "local current = redis.call('INCR', key) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', key, window) " +
            "end " +
            "return current";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static {
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setScriptText(LUA_SCRIPT);
        REDIS_SCRIPT.setResultType(Long.class);
    }

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查是否超过限流阈值
     *
     * @param key           限流 key（如 "rate:limit:login:192.168.1.1"）
     * @param maxRequests   时间窗口内允许的最大请求数
     * @param windowSeconds 时间窗口大小（秒）
     * @return 限流检查结果
     */
    public RateLimitResult checkRateLimit(String key, int maxRequests, long windowSeconds) {
        try {
            List<String> keys = Collections.singletonList(key);
            Long currentCount = redisTemplate.execute(REDIS_SCRIPT, keys, String.valueOf(windowSeconds));

            if (currentCount == null) {
                log.warn("Redis Lua 脚本返回 null，限流失效，放行请求");
                return new RateLimitResult(true, 0, maxRequests);
            }

            boolean allowed = currentCount <= maxRequests;
            long remaining = Math.max(0, maxRequests - currentCount);

            if (!allowed) {
                log.debug("限流触发: key={}, 当前={}, 上限={}", key, currentCount, maxRequests);
            }

            return new RateLimitResult(allowed, remaining, maxRequests);
        } catch (Exception e) {
            // Redis 异常时放行，避免影响正常业务
            log.warn("限流检查异常，放行请求: key={}, error={}", key, e.getMessage());
            return new RateLimitResult(true, maxRequests, maxRequests);
        }
    }

    /**
     * 限流检查结果
     */
    public record RateLimitResult(boolean allowed, long remaining, int maxRequests) {
    }
}
