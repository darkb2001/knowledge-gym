package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.SessionInvalidationPort;
import com.knowledgegym.identity.domain.port.TokenService;

import java.time.Instant;

/**
 * Logout — revoke TOÀN BỘ family (PG + Redis), không chỉ 1 token.
 * Parse JWT để lấy familyId kể cả khi PG miss (Redis-only / race).
 *
 * Đồng thời cắt access token đang lưu hành của user (tokens_invalid_before): JWT stateless nên
 * nếu chỉ revoke refresh family thì access token cũ vẫn gọi API được tới khi hết hạn (15 phút).
 * Cắt bằng cờ user-level, KHÔNG revoke refresh của family khác → thiết bị khác tự refresh,
 * không bị đăng xuất dây chuyền.
 */
public class LogoutUseCase {

    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final SessionInvalidationPort sessionInvalidation;

    public LogoutUseCase(TokenService tokenService,
                          RefreshTokenRepository refreshTokenRepository,
                          RefreshTokenCachePort cache,
                          SessionInvalidationPort sessionInvalidation) {
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.sessionInvalidation = sessionInvalidation;
    }

    public void execute(String rawRefreshToken) {
        String hash = HashUtils.sha256Hex(rawRefreshToken);
        Instant cutoff = Instant.now();

        // Ưu tiên parse JWT → familyId (hoạt động cả khi PG miss)
        try {
            TokenService.RefreshTokenClaims claims = tokenService.verifyRefreshToken(rawRefreshToken);
            refreshTokenRepository.revokeFamily(claims.familyId());
            cache.revokeFamily(claims.familyId(), RefreshToken.TTL);
            cache.blacklist(hash, RefreshToken.TTL);
            sessionInvalidation.invalidateIssuedBefore(claims.userId(), cutoff);
            return;
        } catch (Exception ignored) {
            // Token invalid/expired — fallback PG lookup theo hash
        }

        refreshTokenRepository.findByTokenHash(hash).ifPresent(audit -> {
            refreshTokenRepository.revokeFamily(audit.getFamilyId());
            cache.revokeFamily(audit.getFamilyId(), RefreshToken.TTL);
            cache.blacklist(hash, RefreshToken.TTL);
            sessionInvalidation.invalidateIssuedBefore(audit.getUserId(), cutoff);
        });
    }
}
