package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.NotificationPreferenceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataNotificationPreferenceRepository extends JpaRepository<NotificationPreferenceJpaEntity, UUID> {
    List<NotificationPreferenceJpaEntity> findByUserId(UUID userId);
}