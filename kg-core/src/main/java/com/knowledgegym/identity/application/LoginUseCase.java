package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;

import java.util.UUID;

/**
 * Login flow: email + password → access JWT 15m + refresh 7d (Redis cache + PG audit).
 *
 * Refresh token xoay vòng (rotation): mỗi lần refresh → old token blacklist, new token sinh ra
 * trong cùng family. Reuse detection: old token nào blacklist bị dùng lại → revoke toàn bộ family.
 */
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;

    public LoginUseCase(UserRepository userRepository, PasswordHasher passwordHasher,
                        TokenService tokenService, RefreshTokenRepository refreshTokenRepository,
                        RefreshTokenCachePort cache) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
    }

    public record AuthResult(String accessToken, String refreshToken, UUID userId,
                             String role, String displayName) {}

    public AuthResult execute(String email, String password, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(User.normalizeEmail(email))
                .orElseThrow(() -> new AuthException("Invalid email or password"));

        if (user.getPasswordHash() == null ||
                !passwordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthException("Invalid email or password");
        }

        String accessToken = tokenService.generateAccessToken(user.getId(), user.getRole().name());
        UUID familyId = UUID.randomUUID();
        String rawRefresh = tokenService.generateRefreshToken(user.getId(), familyId);
        String refreshHash = HashUtils.sha256Hex(rawRefresh);

        // PostgreSQL audit
        RefreshToken entity = new RefreshToken(user.getId(), refreshHash, familyId);
        entity.setIpAddress(ipAddress);
        entity.setUserAgent(userAgent);
        refreshTokenRepository.save(entity);

        // Redis cache — TTL 7d
        cache.store(refreshHash, user.getId(), familyId, RefreshToken.TTL);

        return new AuthResult(accessToken, rawRefresh, user.getId(),
                user.getRole().name(), user.getDisplayName());
    }
}