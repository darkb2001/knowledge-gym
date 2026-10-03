package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.identity.domain.model.AuthProvider;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.UserRepository;
import com.knowledgegym.infrastructure.persistence.entity.UserJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataUserRepository;
import com.knowledgegym.shared.domain.model.UserRole;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound adapter: map {@link UserJpaEntity} (JPA) ↔ {@link User} (domain).
 *
 * Quy ước mapping:
 * - Domain chỉ giữ field business; cột phụ (email_verified, xp) được preserve khi update
 *   để adapter không phá dữ liệu ngoài domain scope.
 * - Enum domain (UserRole, AuthProvider) ↔ UPPERCASE string khớp CHECK constraint.
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository repository;

    public UserRepositoryAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(User.normalizeEmail(email)).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(User.normalizeEmail(email));
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = repository.findById(user.getId()).orElseGet(UserJpaEntity::new);
        applyDomainFields(user, entity);
        return toDomain(repository.save(entity));
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private void applyDomainFields(User user, UserJpaEntity entity) {
        entity.setId(user.getId());
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setDisplayName(user.getDisplayName());
        entity.setAvatarUrl(user.getAvatarUrl());
        entity.setRole(user.getRole().name());
        entity.setAuthProvider(user.getAuthProvider().name());
        entity.setOauthId(user.getOauthId());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        // Google OAuth đã verify email_verified=true ở handler
        if (user.isEmailVerified() && !entity.isEmailVerified()) {
            entity.setEmailVerified(true);
            entity.setEmailVerifiedAt(java.time.Instant.now());
        }
    }

    private User toDomain(UserJpaEntity entity) {
        User user = new User(entity.getId());
        user.setEmail(entity.getEmail());
        if (entity.getPasswordHash() != null) {
            user.setPasswordHash(entity.getPasswordHash());
        }
        user.setDisplayName(entity.getDisplayName());
        user.setAvatarUrl(entity.getAvatarUrl());
        user.setRole(UserRole.valueOf(entity.getRole()));
        user.setAuthProvider(AuthProvider.fromDb(entity.getAuthProvider()));
        user.setOauthId(entity.getOauthId());
        if (entity.isEmailVerified()) user.verifyEmail();
        user.setCreatedAt(entity.getCreatedAt());
        user.setUpdatedAt(entity.getUpdatedAt());
        return user;
    }
}