package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.SrsDeckJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataSrsDeckRepository extends JpaRepository<SrsDeckJpaEntity, UUID> {
    List<SrsDeckJpaEntity> findByUserId(UUID userId);
}