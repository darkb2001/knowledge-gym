package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis implementation — 3 key pattern theo docs/12-redis-strategy.md:
 *   rt:{hash} → "userId:familyId", rt:blacklist:{hash}, rt:revoked:{familyId}.
 * StringRedisTemplate (lettuce) — O(1) lookup, TTL tự expire sau 7d.
 */
@Component
public class RedisRefreshTokenCacheAdapter implements RefreshTokenCachePort {

    private static final String PREFIX_ACTIVE = "rt:";
    private static final String PREFIX_BLACKLIST = "rt:blacklist:";
    private static final String PREFIX_REVOKED_FAMILY = "rt:revoked:";

    private final StringRedisTemplate redis;

    public RedisRefreshTokenCacheAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void store(String tokenHash, UUID userId, UUID familyId, Duration ttl) {
        redis.opsForValue().set(PREFIX_ACTIVE + tokenHash, userId + ":" + familyId, ttl);
    }

    @Override
    public Optional<CacheEntry> find(String tokenHash) {
        String value = redis.opsForValue().get(PREFIX_ACTIVE + tokenHash);
        if (value == null) return Optional.empty();
        String[] parts = value.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        return Optional.of(new CacheEntry(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
    }

    @Override
    public void blacklist(String tokenHash, Duration ttl) {
        // Xóa active key + set blacklist — tránh Redis rebuild/confusion sau rotation
        redis.delete(PREFIX_ACTIVE + tokenHash);
        redis.opsForValue().set(PREFIX_BLACKLIST + tokenHash, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String tokenHash) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX_BLACKLIST + tokenHash));
    }

    @Override
    public void revokeFamily(UUID familyId, Duration ttl) {
        redis.opsForValue().set(PREFIX_REVOKED_FAMILY + familyId, "1", ttl);
    }

    @Override
    public boolean isFamilyRevoked(UUID familyId) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX_REVOKED_FAMILY + familyId));
    }
}