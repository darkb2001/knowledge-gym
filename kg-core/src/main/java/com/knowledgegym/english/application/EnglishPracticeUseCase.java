package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishAttempt;
import com.knowledgegym.english.domain.port.EnglishAttemptRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class EnglishPracticeUseCase {
    private final EnglishAttemptRepository repository;
    private final EnglishCatalog catalog;
    public EnglishPracticeUseCase(EnglishAttemptRepository repository, EnglishCatalog catalog) {
        this.repository = repository; this.catalog = catalog;
    }
    public EnglishAttempt start(UUID user, String exerciseId) {
        Objects.requireNonNull(user); catalog.get(exerciseId);
        return repository.startOrResume(user, exerciseId);
    }
    public EnglishAttempt get(UUID user, UUID id) {
        return repository.findOwned(user, id).orElseThrow(() -> new NotFoundException("English attempt not found"));
    }
    public EnglishAttemptRepository.Page list(UUID user, int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid pagination");
        return repository.listOwned(user, page, size);
    }
    public EnglishAttempt save(UUID user, UUID id, long version, Map<String, Integer> answers,
                               String response, int elapsedSeconds, boolean submit) {
        var old = get(user, id);
        var exercise = catalog.get(old.exerciseId());
        if (version < 0 || elapsedSeconds < 0 || elapsedSeconds > 7200)
            throw new IllegalArgumentException("Invalid version or elapsed time");
        if (response == null || response.length() > 20000 || answers == null || answers.size() > 40)
            throw new IllegalArgumentException("Response must be at most 20000 characters");
        for (var entry : answers.entrySet()) {
            var item = exercise.items().stream().filter(q -> q.id().equals(entry.getKey())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question"));
            if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() >= item.options().size())
                throw new IllegalArgumentException("Invalid answer choice");
        }
        if (submit) {
            if (!exercise.items().isEmpty() && answers.size() != exercise.items().size())
                throw new IllegalArgumentException("Answer every question before submitting");
            if (exercise.items().isEmpty() && response.isBlank())
                throw new IllegalArgumentException("Write a response or speaking reflection before submitting");
        }
        // An identical retry of the successful final PUT is safe; different/stale writes conflict.
        if ("SUBMITTED".equals(old.status()) && submit && old.version() == version + 1
                && old.answers().equals(answers) && old.response().equals(response)
                && old.elapsedSeconds() == elapsedSeconds) return old;
        if (!"DRAFT".equals(old.status()) || old.version() != version)
            throw new ConflictException("This attempt changed. Reopen it before saving; submitted work is read-only.");
        var updated = new EnglishAttempt(id, user, old.exerciseId(), submit ? "SUBMITTED" : "DRAFT",
            version + 1, Map.copyOf(answers), response, elapsedSeconds, old.createdAt(), Instant.now());
        if (!repository.updateOwned(updated, version)) throw new ConflictException("This attempt changed. Reopen it before saving.");
        return updated;
    }
}
