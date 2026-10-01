package com.health.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Redis contract fixture: shared persistent values and atomic lock/publish operations. */
public class RedisMemoryFixture {
    public final Map<String, String> data = new ConcurrentHashMap<>();
    public final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    public final ValueOperations<String, String> values = mock(ValueOperations.class);

    public RedisMemoryFixture() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> data.get(i.getArgument(0)));
        doAnswer(i -> {
            data.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString());
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(i -> data.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(i -> {
                    List<String> keys = i.getArgument(1);
                    Object[] args = (Object[]) i.getRawArguments()[2];
                    synchronized (data) {
                        if (!args[0].equals(data.get(keys.get(0)))) {
                            return 0L;
                        }
                        if (keys.size() == 2) {
                            data.put(keys.get(1), (String) args[1]);
                        } else {
                            data.remove(keys.get(0));
                        }
                        return 1L;
                    }
                });
    }
}
