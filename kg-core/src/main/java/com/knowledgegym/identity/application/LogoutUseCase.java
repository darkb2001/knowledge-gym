package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;

public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;

    public LogoutUseCase(RefreshTokenRepository refreshTokenRepository, RefreshTokenCachePort cache) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
    }

    public void execute(String rawRefreshToken) {
        String hash = HashUtils.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(audit -> {
            audit.revoke(null);
            refreshTokenRepository.save(audit);
            cache.revokeFamily(audit.getFamilyId(), RefreshToken.TTL);
            cache.blacklist(hash, RefreshToken.TTL);
        });
    }
}