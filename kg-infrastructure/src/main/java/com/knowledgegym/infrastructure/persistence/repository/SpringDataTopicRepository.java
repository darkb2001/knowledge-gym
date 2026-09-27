package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.TopicJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataTopicRepository extends JpaRepository<TopicJpaEntity, UUID> {
    Optional<TopicJpaEntity> findBySlug(String slug);
}