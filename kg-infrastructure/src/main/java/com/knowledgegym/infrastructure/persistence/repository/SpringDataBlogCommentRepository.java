package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.BlogCommentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataBlogCommentRepository extends JpaRepository<BlogCommentJpaEntity, UUID> {
    List<BlogCommentJpaEntity> findByPostId(UUID postId);
}