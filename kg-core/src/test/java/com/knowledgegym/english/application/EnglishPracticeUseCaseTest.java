package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishAttempt;
import com.knowledgegym.english.domain.port.EnglishAttemptRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnglishPracticeUseCaseTest {
    private final EnglishAttemptRepository repository = mock(EnglishAttemptRepository.class);
    private final EnglishCatalog catalog = new EnglishCatalog();
    private final EnglishPracticeUseCase useCase = new EnglishPracticeUseCase(repository, catalog);
    private final UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    private EnglishAttempt draft;
    @BeforeEach void prepare() {
        draft = new EnglishAttempt(id, owner, "writing-email-v1", "DRAFT", 0, Map.of(), "", 0, Instant.now(), Instant.now());
        when(repository.findOwned(owner, id)).thenReturn(Optional.of(draft));
        when(repository.updateOwned(any(), anyLong())).thenReturn(true);
    }
    @Test void catalogCoversEveryTaskTypeWithUniqueImmutableIds() {
        assertThat(catalog.list()).hasSize(9).extracting(e -> e.id()).doesNotHaveDuplicates();
        for (var skill : com.knowledgegym.english.domain.model.EnglishExercise.Skill.values())
            assertThat(catalog.list()).anyMatch(e -> e.skill() == skill);
        assertThat(catalog.get("writing-email-v1").minimumWords()).isEqualTo(120);
        assertThat(catalog.get("writing-essay-v1").minimumWords()).isEqualTo(250);
        for (var e : catalog.list()) for (var q : e.items()) assertThat(q.correctIndex()).isBetween(0, q.options().size() - 1);
    }
    @Test void startsOnlyKnownExercises() {
        assertThatThrownBy(() -> useCase.start(owner, "injected")).isInstanceOf(NotFoundException.class);
        verify(repository, never()).startOrResume(any(), any());
    }
    @Test void usesOwnedLookupAndHidesAnotherUsersAttempt() {
        UUID stranger = UUID.randomUUID();
        when(repository.findOwned(stranger, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.save(stranger, id, 0, Map.of(), "text", 0, false)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).updateOwned(any(), anyLong());
    }
    @Test void savesDraftWithVersionAndOwner() {
        var saved = useCase.save(owner, id, 0, Map.of(), "Dear Alex", 10, false);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.userId()).isEqualTo(owner);
        assertThat(saved.status()).isEqualTo("DRAFT");
        verify(repository).updateOwned(saved, 0);
    }
    @Test void writingCanSubmitBelowSuggestedCountWithoutFakeScore() {
        var saved = useCase.save(owner, id, 0, Map.of(), "Short original practice.", 120, true);
        assertThat(saved.status()).isEqualTo("SUBMITTED");
    }
    @Test void blankSubjectiveSubmissionIsRejectedButBlankDraftIsAllowed() {
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of(), " ", 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(useCase.save(owner, id, 0, Map.of(), "", 0, false).status()).isEqualTo("DRAFT");
    }
    @Test void validatesLengthsElapsedAndForeignAnswerIdsBeforeWriting() {
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of(), "x".repeat(20001), 0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of(), "ok", 7201, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of("bogus", 0), "ok", 0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.save(owner, id, -1, Map.of(), "ok", 0, false)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).updateOwned(any(), anyLong());
    }
    @Test void objectiveSubmissionRequiresEveryValidAnswer() {
        var objective = new EnglishAttempt(id, owner, "listening-announcement-v1", "DRAFT", 0, Map.of(), "", 0, Instant.now(), Instant.now());
        when(repository.findOwned(owner, id)).thenReturn(Optional.of(objective));
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of("q1", 1), "", 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of("q1", 4), "", 0, false)).isInstanceOf(IllegalArgumentException.class);
        var saved = useCase.save(owner, id, 0, Map.of("q1", 1, "q2", 2, "q3", 0), "", 60, true);
        assertThat(saved.answers()).hasSize(3);
    }
    @Test void staleVersionsAndRacingWritesConflictWithoutOverwrite() {
        assertThatThrownBy(() -> useCase.save(owner, id, 1, Map.of(), "stale", 0, false)).isInstanceOf(ConflictException.class);
        when(repository.updateOwned(any(), anyLong())).thenReturn(false);
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of(), "race", 0, false)).isInstanceOf(ConflictException.class);
    }
    @Test void submittedWorkIsImmutableAndExactFinalRetryIsIdempotent() {
        var finalAttempt = new EnglishAttempt(id, owner, draft.exerciseId(), "SUBMITTED", 1, Map.of(), "My response", 10, draft.createdAt(), Instant.now());
        when(repository.findOwned(owner, id)).thenReturn(Optional.of(finalAttempt));
        assertThat(useCase.save(owner, id, 0, Map.of(), "My response", 10, true)).isSameAs(finalAttempt);
        assertThatThrownBy(() -> useCase.save(owner, id, 0, Map.of(), "Changed", 10, true)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> useCase.save(owner, id, 1, Map.of(), "My response", 10, false)).isInstanceOf(ConflictException.class);
        verify(repository, never()).updateOwned(any(), anyLong());
    }
    @Test void paginationIsBounded() {
        assertThatThrownBy(() -> useCase.list(owner, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.list(owner, 1, 51)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.list(owner, 10001, 10)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).listOwned(any(), anyInt(), anyInt());
    }
}
