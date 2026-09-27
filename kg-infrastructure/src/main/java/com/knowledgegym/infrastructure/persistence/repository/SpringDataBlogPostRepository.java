package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.BlogPostJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataBlogPostRepository extends JpaRepository<BlogPostJpaEntity, UUID> {
    Optional<BlogPostJpaEntity> findBySlug(String slug);
}