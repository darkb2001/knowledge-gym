package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.BlogGenerationQueueJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataBlogGenerationQueueRepository extends JpaRepository<BlogGenerationQueueJpaEntity, UUID> {
}