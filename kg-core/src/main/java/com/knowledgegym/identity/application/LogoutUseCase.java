package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.identity.domain.port.SessionInvalidationPort;
import com.knowledgegym.identity.domain.port.TokenService;

import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

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
        execute(rawRefreshToken, null);
    }

    /** authenticatedUser must come from the verified principal, never from request body. */
    @Transactional
    public void execute(String rawRefreshToken, UUID authenticatedUser) {
        String hash = rawRefreshToken == null ? null : HashUtils.sha256Hex(rawRefreshToken);
        TokenService.RefreshTokenClaims claims = null;
        if (rawRefreshToken != null) {
            try {
                claims = tokenService.verifyRefreshToken(rawRefreshToken);
            } catch (RuntimeException invalidToken) {
                claims = refreshTokenRepository.findByTokenHash(hash)
                        .map(audit -> new TokenService.RefreshTokenClaims(audit.getUserId(), audit.getFamilyId()))
                        .orElse(null);
            }
        }
        if (claims != null && authenticatedUser != null && !authenticatedUser.equals(claims.userId())) {
            throw new AuthException(AuthException.Kind.BAD_REQUEST, "Refresh token does not match authenticated user");
        }
        UUID user = claims == null ? authenticatedUser : claims.userId();
        if (claims != null) refreshTokenRepository.revokeFamily(claims.familyId());
        if (user != null) sessionInvalidation.invalidateIssuedBefore(user, Instant.now());
        // Mutation failures must propagate: they are not invalid-token errors or successful logout.
        if (claims != null) {
            cache.revokeFamily(claims.familyId(), RefreshToken.TTL);
            cache.blacklist(hash, RefreshToken.TTL);
        }
    }
}
