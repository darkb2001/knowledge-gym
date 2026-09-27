package com.knowledgegym.infrastructure.persistence.repository;

import com.knowledgegym.infrastructure.persistence.entity.NoteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataNoteRepository extends JpaRepository<NoteJpaEntity, UUID> {
    List<NoteJpaEntity> findByUserId(UUID userId);
    List<NoteJpaEntity> findByUserIdAndModuleId(UUID userId, UUID moduleId);
    List<NoteJpaEntity> findByUserIdAndQuestionId(UUID userId, UUID questionId);
}