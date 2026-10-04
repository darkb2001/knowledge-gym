package com.knowledgegym.notes.domain.port;

import com.knowledgegym.notes.domain.model.Note;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository {
    List<Note> findByUser(UUID userId);

    List<Note> findBookmarks(UUID userId);

    Optional<Note> findOwned(UUID userId, UUID noteId);

    Note insert(Note note);

    Note update(Note note);

    boolean deleteOwned(UUID userId, UUID noteId);

    boolean questionExists(UUID questionId);

    /** New links and SRS conversion require published content; existing private notes remain readable. */
    default boolean publishedQuestionExists(UUID questionId) { throw new UnsupportedOperationException(); }

    UUID upsertSrsCardFromNote(UUID userId, UUID questionId, UUID noteId);
}
