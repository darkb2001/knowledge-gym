package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshTokenRepository;
import com.knowledgegym.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataRefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository repository;

    public RefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity entity = repository.findById(token.getId()).orElseGet(RefreshTokenJpaEntity::new);
        entity.setId(token.getId());
        entity.setUserId(token.getUserId());
        entity.setTokenHash(token.getTokenHash());
        entity.setFamilyId(token.getFamilyId());
        entity.setCreatedAt(token.getCreatedAt());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setRevokedAt(token.getRevokedAt());
        entity.setReplacedBy(token.getReplacedBy());
        entity.setIpAddress(token.getIpAddress());
        entity.setUserAgent(token.getUserAgent());
        repository.save(entity);
        return token;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, Instant.now());
    }

    @Override
    public Optional<RefreshToken> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public void revokeAllByUserId(UUID userId) {
        repository.revokeAllByUserId(userId, Instant.now());
    }

    @Override
    public Set<UUID> findActiveFamilyIdsByUserId(UUID userId) {
        return repository.findActiveFamilyIdsByUserId(userId, Instant.now());
    }

    @Override
    @Transactional
    public boolean revokeIfActive(UUID tokenId, UUID replacedByTokenId) {
        return repository.revokeIfActive(tokenId, replacedByTokenId, Instant.now()) > 0;
    }

    private RefreshToken toDomain(RefreshTokenJpaEntity e) {
        RefreshToken t = new RefreshToken();
        t.setId(e.getId());
        t.setUserId(e.getUserId());
        t.setTokenHash(e.getTokenHash());
        t.setFamilyId(e.getFamilyId());
        t.setCreatedAt(e.getCreatedAt());
        t.setExpiresAt(e.getExpiresAt());
        t.setRevokedAt(e.getRevokedAt());
        t.setReplacedBy(e.getReplacedBy());
        t.setIpAddress(e.getIpAddress());
        t.setUserAgent(e.getUserAgent());
        return t;
    }
}