package com.knowledgegym.notes.application;

import com.knowledgegym.notes.domain.model.Note;
import com.knowledgegym.shared.domain.model.SearchHit;
import com.knowledgegym.notes.domain.port.NoteRepository;
import com.knowledgegym.shared.application.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotesUseCaseTest {
    private final InMemoryNotes notes = new InMemoryNotes();
    private final NotesUseCase useCase = new NotesUseCase(notes);
    private final UUID user = UUID.randomUUID();
    private final UUID question = UUID.randomUUID();

    @Test
    void createRejectsUnknownQuestionAndConvertRequiresQuestionLink() {
        notes.existingQuestions.add(question);
        assertThatThrownBy(() -> useCase.create(user, UUID.randomUUID(), null, "QUICK", "x", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questionId");

        Note orphan = useCase.create(user, null, null, "STUDY", "body", List.of("tag"));
        assertThatThrownBy(() -> useCase.convertToSrsCard(user, orphan.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question");

        Note linked = useCase.create(user, question, null, "QUICK", "linked", List.of());
        UUID card = useCase.convertToSrsCard(user, linked.id());
        assertThat(useCase.convertToSrsCard(user, linked.id())).isEqualTo(card);
    }

    @Test
    void ownershipIsEnforcedOnGetUpdateDelete() {
        notes.existingQuestions.add(question);
        Note note = useCase.create(user, question, null, "BOOKMARK", "bm", List.of());
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.get(stranger, note.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> useCase.delete(stranger, note.id())).isInstanceOf(NotFoundException.class);
        assertThat(useCase.bookmarks(user)).extracting(Note::id).containsExactly(note.id());
        assertThat(useCase.exportMarkdown(user)).contains("# My notes").contains("bm");
    }

    @Test
    void withdrawalBlocksNewLinksAndConversionButKeepsExistingNotesEditable() {
        notes.existingQuestions.add(question);
        var linked = useCase.create(user, question, null, "STUDY", "My notes", List.of());
        notes.unpublishedQuestions.add(question);
        assertThatThrownBy(() -> useCase.create(user, question, null, "BOOKMARK", "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.convertToSrsCard(user, linked.id()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(useCase.update(user, linked.id(), question, null, "STUDY", "Still mine", List.of()).content())
                .isEqualTo("Still mine");
        assertThat(useCase.list(user)).hasSize(1);
        assertThat(notes.cards).isEmpty();
    }

    @Test
    void nullTagElementsAreClientErrorsWithoutChangingExistingNotes() {
        var note = useCase.create(user, null, null, "QUICK", "Keep", List.of());
        var badTags = java.util.Collections.<String>singletonList(null);
        assertThatThrownBy(() -> useCase.create(user, null, null, "QUICK", "Invalid", badTags))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.update(user, note.id(), null, null, "QUICK", "Invalid", badTags))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(useCase.list(user)).hasSize(1);
        assertThat(useCase.get(user, note.id()).content()).isEqualTo("Keep");
    }

    private static final class InMemoryNotes implements NoteRepository {
        private final Map<UUID, Note> byId = new HashMap<>();
        private final Map<String, UUID> cards = new HashMap<>();
        final List<UUID> existingQuestions = new ArrayList<>();
        final List<UUID> unpublishedQuestions = new ArrayList<>();

        @Override public List<Note> findByUser(UUID userId) {
            return byId.values().stream().filter(n -> n.userId().equals(userId)).toList();
        }
        @Override public List<Note> findBookmarks(UUID userId) {
            return findByUser(userId).stream().filter(n -> "BOOKMARK".equals(n.noteType())).toList();
        }
        @Override public Optional<Note> findOwned(UUID userId, UUID noteId) {
            return Optional.ofNullable(byId.get(noteId)).filter(n -> n.userId().equals(userId));
        }
        @Override public Note insert(Note note) { byId.put(note.id(), note); return note; }
        @Override public Note update(Note note) { byId.put(note.id(), note); return note; }
        @Override public boolean deleteOwned(UUID userId, UUID noteId) {
            Note note = byId.get(noteId);
            if (note == null || !note.userId().equals(userId)) return false;
            byId.remove(noteId);
            return true;
        }
        @Override public boolean questionExists(UUID questionId) { return existingQuestions.contains(questionId); }
        @Override public boolean publishedQuestionExists(UUID questionId) {
            return existingQuestions.contains(questionId) && !unpublishedQuestions.contains(questionId);
        }
        @Override public UUID upsertSrsCardFromNote(UUID userId, UUID questionId, UUID noteId) {
            return cards.computeIfAbsent(userId + ":" + questionId, key -> UUID.randomUUID());
        }
    }
}
