package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.UserBadgeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataUserBadgeRepository extends JpaRepository<UserBadgeJpaEntity, UserBadgeJpaEntity.UserBadgeId> {
}