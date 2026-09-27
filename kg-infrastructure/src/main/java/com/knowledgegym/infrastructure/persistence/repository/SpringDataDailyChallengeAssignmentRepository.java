package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.DailyChallengeAssignmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface SpringDataDailyChallengeAssignmentRepository extends JpaRepository<DailyChallengeAssignmentJpaEntity, UUID> {
    DailyChallengeAssignmentJpaEntity findByUserIdAndChallengeDate(UUID userId, LocalDate date);
}