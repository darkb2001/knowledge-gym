package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.infrastructure.persistence.entity.NoteJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataNoteRepository;
import com.knowledgegym.notes.domain.model.Note;
import com.knowledgegym.notes.domain.port.NoteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class NoteRepositoryAdapter implements NoteRepository {
    private final SpringDataNoteRepository jpa;
    private final JdbcTemplate jdbc;

    public NoteRepositoryAdapter(SpringDataNoteRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public List<Note> findByUser(UUID userId) {
        return jpa.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Note> findBookmarks(UUID userId) {
        return jpa.findByUserIdAndNoteTypeOrderByCreatedAtDesc(userId, "BOOKMARK").stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Note> findOwned(UUID userId, UUID noteId) {
        return jpa.findByIdAndUserId(noteId, userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public Note insert(Note note) {
        return toDomain(jpa.save(toEntity(note)));
    }

    @Override
    @Transactional
    public Note update(Note note) {
        NoteJpaEntity entity = jpa.findByIdAndUserId(note.id(), note.userId())
                .orElseThrow(() -> new IllegalStateException("Note disappeared during update"));
        entity.setQuestionId(note.questionId());
        entity.setModuleId(note.moduleId());
        entity.setNoteType(note.noteType());
        entity.setContent(note.content());
        entity.setTags(note.tags().toArray(String[]::new));
        entity.setUpdatedAt(note.updatedAt());
        return toDomain(jpa.save(entity));
    }

    @Override
    @Transactional
    public boolean deleteOwned(UUID userId, UUID noteId) {
        return jpa.deleteByIdAndUserId(noteId, userId) > 0;
    }

    @Override
    public boolean questionExists(UUID questionId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM questions WHERE id=?", Long.class, questionId);
        return count != null && count > 0;
    }

    @Override
    public boolean publishedQuestionExists(UUID questionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM questions WHERE id=? AND content_status='PUBLISHED')",
                Boolean.class, questionId));
    }

    @Override
    @Transactional
    public UUID upsertSrsCardFromNote(UUID userId, UUID questionId, UUID noteId) {
        jdbc.update(
                "INSERT INTO srs_cards(user_id,question_id,source_note_id) VALUES(?,?,?) "
                        + "ON CONFLICT(user_id,question_id) DO UPDATE SET "
                        + "source_note_id=coalesce(srs_cards.source_note_id,EXCLUDED.source_note_id)",
                userId, questionId, noteId);
        return jdbc.queryForObject(
                "SELECT id FROM srs_cards WHERE user_id=? AND question_id=?",
                UUID.class, userId, questionId);
    }

    private Note toDomain(NoteJpaEntity entity) {
        String[] tags = entity.getTags() == null ? new String[0] : entity.getTags();
        return new Note(
                entity.getId(),
                entity.getUserId(),
                entity.getQuestionId(),
                entity.getModuleId(),
                entity.getNoteType(),
                entity.getContent(),
                Arrays.asList(tags),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private NoteJpaEntity toEntity(Note note) {
        NoteJpaEntity entity = new NoteJpaEntity();
        entity.setId(note.id());
        entity.setUserId(note.userId());
        entity.setQuestionId(note.questionId());
        entity.setModuleId(note.moduleId());
        entity.setNoteType(note.noteType());
        entity.setContent(note.content());
        entity.setTags(note.tags().toArray(String[]::new));
        entity.setPublic(false);
        entity.setCreatedAt(note.createdAt());
        entity.setUpdatedAt(note.updatedAt());
        return entity;
    }
}
