package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.QuestionOptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataQuestionOptionRepository extends JpaRepository<QuestionOptionJpaEntity, UUID> {
    List<QuestionOptionJpaEntity> findByQuestionId(UUID questionId);
}