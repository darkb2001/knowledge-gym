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
 * Ordering (sửa multi-tab session-kill từ Redis-first):
 * 1. Reuse checks (family revoked / blacklist) — chỉ revokeFamily khi TRUE reuse
 * 2. Lookup Redis → PG fallback
 * 3. PG CAS revoke old + insert new (atomic winner)
 * 4. Redis store new + blacklist old (sau khi PG thắng)
 *
 * CAS thua (concurrent) → 401 KHÔNG revokeFamily (winner giữ session).
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

        if (cache.isFamilyRevoked(familyId)) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new AuthException("Refresh token family revoked — reuse detected");
        }

        // Blacklisted = đã rotated thành công trước đó → TRUE reuse (stolen/old tab)
        if (cache.isBlacklisted(oldHash)) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new AuthException("Refresh token reuse detected — family revoked");
        }

        Optional<RefreshTokenCachePort.CacheEntry> cacheEntry = cache.find(oldHash);

        if (cacheEntry.isEmpty()) {
            refreshTokenRepository.findByTokenHash(oldHash).ifPresent(audit -> {
                if (audit.isRevoked()) {
                    // PG đã revoke (rotation trước) nhưng Redis miss → reuse
                    refreshTokenRepository.revokeFamily(familyId);
                    throw new AuthException("Refresh token already revoked");
                }
                cache.store(oldHash, audit.getUserId(), audit.getFamilyId(), RefreshToken.TTL);
            });
            if (cache.find(oldHash).isEmpty()) {
                throw new AuthException("Refresh token not found in cache or database");
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User no longer exists"));

        String newAccessToken = tokenService.generateAccessToken(user.getId(), user.getRole().name());
        String newRawRefresh = tokenService.generateRefreshToken(user.getId(), familyId);
        String newHash = HashUtils.sha256Hex(newRawRefresh);

        // PG-first: CAS revoke old — chỉ 1 concurrent thread thắng
        RefreshToken newAudit = new RefreshToken(userId, newHash, familyId);
        newAudit.setIpAddress(ipAddress);
        newAudit.setUserAgent(userAgent);
        RefreshToken savedNew = refreshTokenRepository.save(newAudit);

        Optional<RefreshToken> oldOpt = refreshTokenRepository.findByTokenHash(oldHash);
        if (oldOpt.isPresent()) {
            boolean revoked = refreshTokenRepository.revokeIfActive(oldOpt.get().getId(), savedNew.getId());
            if (!revoked) {
                // Concurrent loser — KHÔNG revokeFamily (winner vẫn valid)
                throw new AuthException("Refresh token already rotated — retry with latest cookie");
            }
        }

        // Redis sau PG thắng — blacklist + store new
        cache.blacklist(oldHash, RefreshToken.TTL);
        cache.store(newHash, userId, familyId, RefreshToken.TTL);

        return new Result(newAccessToken, newRawRefresh, userId, user.getRole().name());
    }
}