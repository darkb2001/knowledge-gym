package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token rotation — 2 lớp (Redis O(1) + PostgreSQL fallback).
 *
 * Sửa race condition (review blocker #1):
 * - Redis-first: blacklist OLD token TRƯỚC khi PG CAS revoke — concurrent request
 *   thứ 2 thấy blacklist → reject ngay, không cần PG check.
 * - PG CAS `revokeIfActive`: nếu cả 2 request đều đến PG (hiếm — Redis miss),
 *   chỉ 1 thành công. Thread thua → AuthException.
 * - `@Transactional`: PG writes toàn bộ hoặc rollback.
 */
public class RefreshTokenUseCase {

    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final UserRepository userRepository;

    public RefreshTokenUseCase(TokenService tokenService,
                                RefreshTokenRepository refreshTokenRepository,
                                RefreshTokenCachePort cache,
                                UserRepository userRepository) {
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.userRepository = userRepository;
    }

    public record Result(String accessToken, String newRefreshToken, UUID userId, String role) {}

    @Transactional
    public Result execute(String rawRefreshToken, String ipAddress, String userAgent) {
        String oldHash = HashUtils.sha256Hex(rawRefreshToken);

        TokenService.RefreshTokenClaims claims;
        try {
            claims = tokenService.verifyRefreshToken(rawRefreshToken);
        } catch (Exception e) {
            throw new AuthException("Invalid refresh token");
        }

        UUID userId = claims.userId();
        UUID familyId = claims.familyId();

        // Reuse detection: family revoked globally (reuse by other tab after legitimate rotation)
        if (cache.isFamilyRevoked(familyId)) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new AuthException("Refresh token family revoked — reuse detected");
        }

        // Token used after blacklisted (reuse by old tab)
        if (cache.isBlacklisted(oldHash)) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new AuthException("Refresh token reuse detected — family revoked");
        }

        // Redis hit → active token OK; miss → PG fallback
        Optional<RefreshTokenCachePort.CacheEntry> cacheEntry = cache.find(oldHash);

        if (cacheEntry.isEmpty()) {
            // Fallback PostgreSQL (Redis restart or TTL expired)
            refreshTokenRepository.findByTokenHash(oldHash).ifPresent(audit -> {
                if (audit.isRevoked()) {
                    refreshTokenRepository.revokeFamily(familyId);
                    throw new AuthException("Refresh token already revoked");
                }
                // Rebuild cache from PG audit
                cache.store(oldHash, audit.getUserId(), audit.getFamilyId(), RefreshToken.TTL);
            });
            if (cache.find(oldHash).isEmpty()) {
                throw new AuthException("Refresh token not found in cache or database");
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User no longer exists"));

        // --- Redis-FIRST: blacklist old token TRƯỚC khi tạo mới ---
        // Concurrent request thứ 2 sẽ thấy isBlacklisted → reject ở trên.
        cache.blacklist(oldHash, RefreshToken.TTL);

        // Rotation: generate new token in same family
        String newAccessToken = tokenService.generateAccessToken(user.getId(), user.getRole().name());
        String newRawRefresh = tokenService.generateRefreshToken(user.getId(), familyId);
        String newHash = HashUtils.sha256Hex(newRawRefresh);

        // PostgreSQL audit — CAS revoke old (atomic: chỉ 1 thread thắng)
        RefreshToken newAudit = new RefreshToken(userId, newHash, familyId);
        newAudit.setIpAddress(ipAddress);
        newAudit.setUserAgent(userAgent);
        RefreshToken savedNew = refreshTokenRepository.save(newAudit);

        refreshTokenRepository.findByTokenHash(oldHash).ifPresent(oldAudit -> {
            boolean revoked = refreshTokenRepository.revokeIfActive(oldAudit.getId(), savedNew.getId());
            if (!revoked) {
                // CAS thua — token đã bị revoke bởi thread khác → coi như reuse
                refreshTokenRepository.revokeFamily(familyId);
                throw new AuthException("Concurrent refresh detected — family revoked");
            }
        });

        // Redis: store new (blacklist đã ghi ở trên)
        cache.store(newHash, userId, familyId, RefreshToken.TTL);

        return new Result(newAccessToken, newRawRefresh, userId, user.getRole().name());
    }
}