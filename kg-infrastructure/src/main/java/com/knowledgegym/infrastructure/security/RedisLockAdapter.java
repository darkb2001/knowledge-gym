package com.knowledgegym.infrastructure.security;

import com.knowledgegym.shared.domain.port.DistributedLockPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.List;

/** Best-effort debounce; row locks and unique constraints protect the transaction. */
@Component
public class RedisLockAdapter implements DistributedLockPort {
    private static final Logger LOG = LoggerFactory.getLogger(RedisLockAdapter.class);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);
    private final StringRedisTemplate redis;

    public RedisLockAdapter(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public boolean tryAcquire(String key, String token, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl));
        } catch (DataAccessException exception) {
            LOG.warn("Quiz debounce unavailable; database guard active");
            return true;
        }
    }

    @Override
    public void release(String key, String token) {
        try {
            redis.execute(UNLOCK, List.of(key), token);
        } catch (DataAccessException exception) {
            LOG.warn("Quiz debounce release unavailable");
        }
    }
}
