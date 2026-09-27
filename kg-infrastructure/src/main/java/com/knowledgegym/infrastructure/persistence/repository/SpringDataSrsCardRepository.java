package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.SrsCardJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSrsCardRepository extends JpaRepository<SrsCardJpaEntity, UUID> {
    Optional<SrsCardJpaEntity> findByUserIdAndQuestionId(UUID userId, UUID questionId);
    List<SrsCardJpaEntity> findByUserIdAndNextReviewLessThanEqual(UUID userId, LocalDate date);
    long countByUserId(UUID userId);
}