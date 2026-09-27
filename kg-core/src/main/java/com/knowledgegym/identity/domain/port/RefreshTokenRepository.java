package com.knowledgegym.identity.domain.port;

import com.knowledgegym.identity.domain.model.RefreshToken;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken token);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void revokeFamily(UUID familyId);
    Optional<RefreshToken> findById(UUID id);
    /** Revoke tất cả refresh token của user — dùng khi reset password. */
    void revokeAllByUserId(UUID userId);
    /** Trả về các family đang active (chưa revoke, chưa expire) của user — cho Redis blacklist. */
    Set<UUID> findActiveFamilyIdsByUserId(UUID userId);
    /** Atomic CAS: revoke token nếu chưa revoke (WHERE revoked_at IS NULL) — trả về false nếu đã bị revoke (race). */
    boolean revokeIfActive(UUID tokenId, UUID replacedByTokenId);
}