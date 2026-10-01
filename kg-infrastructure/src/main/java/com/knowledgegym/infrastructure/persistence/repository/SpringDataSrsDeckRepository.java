package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.SrsDeckJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSrsDeckRepository extends JpaRepository<SrsDeckJpaEntity, UUID> {

    List<SrsDeckJpaEntity> findByUserId(UUID userId);

    /**
     * V004 không có UK `(user_id, module_id)` → query này là guard duy nhất để không tạo deck trùng
     * cho cùng module (xem `EnrollCardsUseCase`).
     */
    Optional<SrsDeckJpaEntity> findByUserIdAndModuleId(UUID userId, UUID moduleId);

    /** Tra 1 deck của đúng chủ sở hữu — thay cho việc load cả list rồi lọc ở application. */
    Optional<SrsDeckJpaEntity> findByIdAndUserId(UUID id, UUID userId);
}
