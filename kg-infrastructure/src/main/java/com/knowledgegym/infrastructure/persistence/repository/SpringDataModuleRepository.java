package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.ModuleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataModuleRepository extends JpaRepository<ModuleJpaEntity, UUID> {
    Optional<ModuleJpaEntity> findBySlug(String slug);
    List<ModuleJpaEntity> findByTopicId(UUID topicId);
}