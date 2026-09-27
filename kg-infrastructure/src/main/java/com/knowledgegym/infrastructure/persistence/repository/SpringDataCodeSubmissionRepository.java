package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.CodeSubmissionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataCodeSubmissionRepository extends JpaRepository<CodeSubmissionJpaEntity, UUID> {
}