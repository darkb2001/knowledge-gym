package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.ChallengeTestCaseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataChallengeTestCaseRepository extends JpaRepository<ChallengeTestCaseJpaEntity, UUID> {
    List<ChallengeTestCaseJpaEntity> findByChallengeId(UUID challengeId);
}