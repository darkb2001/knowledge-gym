package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.QuestionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataQuestionRepository extends JpaRepository<QuestionJpaEntity, UUID> {
    List<QuestionJpaEntity> findByModuleId(UUID moduleId);
}