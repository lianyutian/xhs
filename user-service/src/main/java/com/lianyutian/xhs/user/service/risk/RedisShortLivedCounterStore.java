package com.lianyutian.xhs.user.service.risk;

import java.time.Duration;
import java.util.Collection;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisShortLivedCounterStore implements ShortLivedCounterStore {

    private final StringRedisTemplate redisTemplate;

    public RedisShortLivedCounterStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long get(String key) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            return 0;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value == null) {
            return 0;
        }
        if (value == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return value;
    }

    @Override
    public void delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }
}
