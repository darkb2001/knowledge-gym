package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now " +
           "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now " +
           "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT DISTINCT t.familyId FROM RefreshTokenJpaEntity t " +
           "WHERE t.userId = :userId AND t.revokedAt IS NULL AND t.expiresAt > :now")
    Set<UUID> findActiveFamilyIdsByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /** CAS revoke — chỉ thành công nếu token chưa bị revoke (đồng thời chống double-rotation). */
    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now, t.replacedBy = :replacedBy " +
           "WHERE t.id = :id AND t.revokedAt IS NULL")
    int revokeIfActive(@Param("id") UUID id,
                       @Param("replacedBy") UUID replacedBy,
                       @Param("now") Instant now);

    List<RefreshTokenJpaEntity> findByUserIdAndRevokedAtIsNull(UUID userId);
}