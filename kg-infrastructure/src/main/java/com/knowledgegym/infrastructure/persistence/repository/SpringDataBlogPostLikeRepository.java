package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.BlogPostLikeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataBlogPostLikeRepository extends JpaRepository<BlogPostLikeJpaEntity, UUID> {
}