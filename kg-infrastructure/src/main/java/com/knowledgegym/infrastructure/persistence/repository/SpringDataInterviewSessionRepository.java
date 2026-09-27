package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.InterviewSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataInterviewSessionRepository extends JpaRepository<InterviewSessionJpaEntity, UUID> {
}