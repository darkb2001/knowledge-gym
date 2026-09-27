package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.PasswordResetCodeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataPasswordResetCodeRepository extends JpaRepository<PasswordResetCodeJpaEntity, UUID> {
}