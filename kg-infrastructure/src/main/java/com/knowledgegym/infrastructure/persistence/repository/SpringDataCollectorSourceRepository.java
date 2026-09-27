package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.CollectorSourceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataCollectorSourceRepository extends JpaRepository<CollectorSourceJpaEntity, UUID> {
}