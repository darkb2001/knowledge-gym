package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.QuizAnswerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataQuizAnswerRepository extends JpaRepository<QuizAnswerJpaEntity, UUID> {
    List<QuizAnswerJpaEntity> findBySessionId(UUID sessionId);
}