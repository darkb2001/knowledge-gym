package com.knowledgegym.progress.domain.port;

import com.knowledgegym.progress.domain.model.UserProgress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProgressRepository {
    Optional<UserProgress> find(UUID userId, UUID moduleId);
    List<UserProgress> findAllByUser(UUID userId);
    void upsertAdding(UUID userId, UUID moduleId, int attemptsDelta, int correctDelta, Instant activeAt);
}
