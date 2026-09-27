package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.identity.domain.model.PasswordResetCode;
import com.knowledgegym.identity.domain.port.PasswordResetCodeRepository;
import com.knowledgegym.infrastructure.persistence.entity.PasswordResetCodeJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataPasswordResetCodeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class PasswordResetCodeRepositoryAdapter implements PasswordResetCodeRepository {

    private final SpringDataPasswordResetCodeRepository repository;

    public PasswordResetCodeRepositoryAdapter(SpringDataPasswordResetCodeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public PasswordResetCode save(PasswordResetCode code) {
        PasswordResetCodeJpaEntity entity = repository.findById(code.getId())
                .orElseGet(PasswordResetCodeJpaEntity::new);
        entity.setId(code.getId());
        entity.setUserId(code.getUserId());
        entity.setCodeHash(code.getCodeHash());
        entity.setAttempts(code.getAttempts());
        entity.setUsedAt(code.getUsedAt());
        entity.setExpiresAt(code.getExpiresAt());
        entity.setCreatedAt(code.getCreatedAt());
        repository.save(entity);
        return code;
    }

    @Override
    public Optional<PasswordResetCode> findLatestActiveByUserId(UUID userId) {
        return repository.findLatestActiveByUserId(userId, Instant.now()).map(this::toDomain);
    }

    private PasswordResetCode toDomain(PasswordResetCodeJpaEntity e) {
        PasswordResetCode c = new PasswordResetCode();
        c.setId(e.getId());
        c.setUserId(e.getUserId());
        c.setCodeHash(e.getCodeHash());
        c.setAttempts(e.getAttempts());
        c.setUsedAt(e.getUsedAt());
        c.setExpiresAt(e.getExpiresAt());
        c.setCreatedAt(e.getCreatedAt());
        return c;
    }
}