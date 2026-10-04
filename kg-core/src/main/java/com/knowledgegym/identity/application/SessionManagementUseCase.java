package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.SessionInvalidationPort;
import com.knowledgegym.identity.domain.port.SessionRegistryPort;
import com.knowledgegym.identity.domain.port.TokenService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý thiết bị/phiên đăng nhập (mỗi phiên = một refresh-token family).
 *
 * Vì mỗi lần đăng nhập sinh family mới, nhiều thiết bị dùng cùng lúc không đá nhau. Ở đây chỉ
 * revoke family của CHÍNH user và chỉ family được chọn ⇒ "đăng xuất thiết bị khác" không làm mất
 * phiên đang dùng. Khi revoke, cắt luôn access token đang lưu hành (tokens_invalid_before) vì JWT
 * là stateless — không cắt thì thiết bị vừa bị đăng xuất vẫn gọi API được thêm tới 15 phút.
 */
public class SessionManagementUseCase {

    private final SessionRegistryPort registry;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final SessionInvalidationPort sessionInvalidation;
    private final TokenService tokenService;

    public SessionManagementUseCase(SessionRegistryPort registry,
                                    RefreshTokenRepository refreshTokenRepository,
                                    RefreshTokenCachePort cache,
                                    SessionInvalidationPort sessionInvalidation,
                                    TokenService tokenService) {
        this.registry = registry;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.sessionInvalidation = sessionInvalidation;
        this.tokenService = tokenService;
    }

    public record SessionView(String familyId, Instant createdAt, Instant lastSeenAt,
                              String ipAddress, String userAgent, boolean current) {}

    public record RevokeResult(boolean removed, boolean wasCurrent) {}

    public List<SessionView> list(UUID userId, String rawRefreshToken) {
        UUID currentFamily = currentFamily(rawRefreshToken, userId);
        return registry.listActive(userId).stream()
                .map(s -> new SessionView(s.familyId().toString(), s.createdAt(), s.lastSeenAt(),
                        s.ipAddress(), s.userAgent(), s.familyId().equals(currentFamily)))
                .toList();
    }

    /** Revoke một phiên. Chỉ chấp nhận family thuộc chính user (không revoke thiết bị người khác). */
    public RevokeResult revoke(UUID userId, UUID familyId, String rawRefreshToken) {
        if (userId == null || familyId == null) {
            return new RevokeResult(false, false);
        }
        boolean owned = registry.listActive(userId).stream().anyMatch(s -> s.familyId().equals(familyId));
        if (!owned) {
            return new RevokeResult(false, false);
        }
        boolean wasCurrent = familyId.equals(currentFamily(rawRefreshToken, userId));
        revokeFamily(userId, familyId);
        return new RevokeResult(true, wasCurrent);
    }

    /** Đăng xuất mọi thiết bị khác, giữ phiên hiện tại. Trả về số phiên đã revoke. */
    public int revokeOthers(UUID userId, String rawRefreshToken) {
        UUID currentFamily = currentFamily(rawRefreshToken, userId);
        int revoked = 0;
        for (SessionRegistryPort.SessionSummary s : registry.listActive(userId)) {
            if (s.familyId().equals(currentFamily)) {
                continue;
            }
            revokeFamily(userId, s.familyId());
            revoked++;
        }
        return revoked;
    }

    /** familyId của phiên đang gọi API (lấy từ refresh cookie); null nếu cookie thiếu/không hợp lệ. */
    private UUID currentFamily(String rawRefreshToken, UUID userId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }
        try {
            TokenService.RefreshTokenClaims claims = tokenService.verifyRefreshToken(rawRefreshToken);
            return claims != null && claims.userId() != null && claims.userId().equals(userId)
                    ? claims.familyId() : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private void revokeFamily(UUID userId, UUID familyId) {
        refreshTokenRepository.revokeFamily(familyId);
        cache.revokeFamily(familyId, RefreshToken.TTL);
        sessionInvalidation.invalidateIssuedBefore(userId, Instant.now());
    }
}
