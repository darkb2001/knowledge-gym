package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.ChallengeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataChallengeRepository extends JpaRepository<ChallengeJpaEntity, UUID> {
    Optional<ChallengeJpaEntity> findBySlug(String slug);
}