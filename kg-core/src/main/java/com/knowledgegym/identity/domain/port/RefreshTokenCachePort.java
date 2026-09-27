package com.knowledgegym.identity.domain.port;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis layer O(1) cho refresh token — 2 lớp cùng với PostgreSQL audit.
 * Key patterns (docs/12-redis-strategy.md):
 *   rt:{token_hash}          = "userId:familyId" TTL 7d — active lookup
 *   rt:blacklist:{token_hash}= 1                        TTL 7d — reuse detection
 *   rt:revoked:{familyId}    = 1                        TTL 7d — family revoke
 */
public interface RefreshTokenCachePort {

    record CacheEntry(UUID userId, UUID familyId) {}

    void store(String tokenHash, UUID userId, UUID familyId, Duration ttl);
    Optional<CacheEntry> find(String tokenHash);

    void blacklist(String tokenHash, Duration ttl);
    boolean isBlacklisted(String tokenHash);

    void revokeFamily(UUID familyId, Duration ttl);
    boolean isFamilyRevoked(UUID familyId);
}