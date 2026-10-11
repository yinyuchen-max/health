package com.health.service;

import com.health.common.config.TrafficProtectionProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/** Redis 令牌桶：所有 IP、用户和接口共享同一桶，Lua 原子扣减。 */
public class GlobalTrafficLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource("traffic/global-token-bucket.lua"));
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final TrafficProtectionProperties properties;

    public GlobalTrafficLimiter(StringRedisTemplate redis, TrafficProtectionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 0 表示放行，正数表示最早重试等待毫秒数；Redis 故障交由入口返回 503。 */
    public long acquire() {
        Long retryMillis = redis.execute(SCRIPT, List.of(properties.getRedisKey()),
                String.valueOf(properties.getRequestsPerSecond()), String.valueOf(properties.getBurstCapacity()));
        if (retryMillis == null || retryMillis < 0) {
            throw new IllegalStateException("全局限流脚本未返回有效结果");
        }
        return retryMillis;
    }
}
