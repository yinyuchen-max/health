package com.health.memory;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

@Component
public class WorkingMemoryStore {
    private final RedisTemplate<String,Object> redis;
    public WorkingMemoryStore(RedisTemplate<String,Object> redis) { this.redis=redis; }
    public void buffer(Long user,String value) {
        try {
            String key="memory:buffer:"+user;
            redis.opsForList().rightPush(key,value);
            redis.opsForList().trim(key,-32,-1);
            redis.expire(key,Duration.ofHours(1));
        } catch(Exception ignored) { /* L0 is disposable; durable encoding work lives in the outbox. */ }
    }
    public void working(Long user,String value) {
        try { redis.opsForValue().set("memory:working:"+user,value,Duration.ofMinutes(15)); }
        catch(Exception ignored) { /* Never fail chat because a derived context cache is unavailable. */ }
    }
    public void clearStrict(Long user) { redis.delete(List.of("memory:buffer:"+user,"memory:working:"+user)); }
}
