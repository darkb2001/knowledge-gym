package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.UserProgressJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataUserProgressRepository extends JpaRepository<UserProgressJpaEntity, UUID> {
    Optional<UserProgressJpaEntity> findByUserIdAndModuleId(UUID userId, UUID moduleId);
}