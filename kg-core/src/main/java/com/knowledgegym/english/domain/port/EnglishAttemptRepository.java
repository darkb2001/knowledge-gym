package com.knowledgegym.english.domain.port;

import com.knowledgegym.english.domain.model.EnglishAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnglishAttemptRepository {
    record Page(List<EnglishAttempt> items, long totalElements, int page, int size) {}
    EnglishAttempt startOrResume(UUID user, String exerciseId);
    Optional<EnglishAttempt> findOwned(UUID user, UUID id);
    Page listOwned(UUID user, int page, int size);
    boolean updateOwned(EnglishAttempt attempt, long expectedVersion);
}
