package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.NoteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataNoteRepository extends JpaRepository<NoteJpaEntity, UUID> {
    List<NoteJpaEntity> findByUserId(UUID userId);

    List<NoteJpaEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<NoteJpaEntity> findByUserIdAndNoteTypeOrderByCreatedAtDesc(UUID userId, String noteType);

    List<NoteJpaEntity> findByUserIdAndModuleId(UUID userId, UUID moduleId);

    List<NoteJpaEntity> findByUserIdAndQuestionId(UUID userId, UUID questionId);

    Optional<NoteJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("delete from NoteJpaEntity n where n.id = :id and n.userId = :userId")
    int deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
