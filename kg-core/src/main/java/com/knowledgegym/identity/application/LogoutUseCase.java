package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.TokenService;

/**
 * Logout — revoke TOÀN BỘ family (PG + Redis), không chỉ 1 token.
 * Parse JWT để lấy familyId kể cả khi PG miss (Redis-only / race).
 */
public class LogoutUseCase {

    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;

    public LogoutUseCase(TokenService tokenService,
                          RefreshTokenRepository refreshTokenRepository,
                          RefreshTokenCachePort cache) {
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
    }

    public void execute(String rawRefreshToken) {
        String hash = HashUtils.sha256Hex(rawRefreshToken);

        // Ưu tiên parse JWT → familyId (hoạt động cả khi PG miss)
        try {
            TokenService.RefreshTokenClaims claims = tokenService.verifyRefreshToken(rawRefreshToken);
            refreshTokenRepository.revokeFamily(claims.familyId());
            cache.revokeFamily(claims.familyId(), RefreshToken.TTL);
            cache.blacklist(hash, RefreshToken.TTL);
            return;
        } catch (Exception ignored) {
            // Token invalid/expired — fallback PG lookup theo hash
        }

        refreshTokenRepository.findByTokenHash(hash).ifPresent(audit -> {
            refreshTokenRepository.revokeFamily(audit.getFamilyId());
            cache.revokeFamily(audit.getFamilyId(), RefreshToken.TTL);
            cache.blacklist(hash, RefreshToken.TTL);
        });
    }
}