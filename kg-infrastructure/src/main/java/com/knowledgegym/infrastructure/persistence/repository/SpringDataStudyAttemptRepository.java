package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.StudyAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataStudyAttemptRepository extends JpaRepository<StudyAttemptJpaEntity, UUID> {
    List<StudyAttemptJpaEntity> findByUserIdOrderByAttemptedAtDesc(UUID userId);
    List<StudyAttemptJpaEntity> findByUserIdAndQuestionId(UUID userId, UUID questionId);
}