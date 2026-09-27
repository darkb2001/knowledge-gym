package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.QuizSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataQuizSessionRepository extends JpaRepository<QuizSessionJpaEntity, UUID> {
}