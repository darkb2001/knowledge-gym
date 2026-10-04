package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.ContentTrackJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataContentTrackRepository extends JpaRepository<ContentTrackJpaEntity, String> {
}
