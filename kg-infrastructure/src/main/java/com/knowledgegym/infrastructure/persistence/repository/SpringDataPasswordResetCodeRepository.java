package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.PasswordResetCodeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataPasswordResetCodeRepository extends JpaRepository<PasswordResetCodeJpaEntity, UUID> {

    /** Code mới nhất của user — chưa dùng, chưa hết hạn. */
    @Query("""
            SELECT c FROM PasswordResetCodeJpaEntity c
            WHERE c.userId = :userId AND c.usedAt IS NULL AND c.expiresAt > :now
            ORDER BY c.createdAt DESC
            """)
    Optional<PasswordResetCodeJpaEntity> findLatestActiveByUserId(
            @Param("userId") UUID userId, @Param("now") Instant now);
}