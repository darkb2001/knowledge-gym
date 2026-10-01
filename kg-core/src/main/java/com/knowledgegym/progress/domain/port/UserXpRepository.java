package com.knowledgegym.progress.domain.port;

import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.progress.domain.model.UserXpRow;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface UserXpRepository {
    record FirstAttempt(UUID attemptId, Instant attemptedAt, AttemptSource source, boolean correct) {}

    void lockQuestion(UUID userId, UUID questionId);
    Map<UUID, FirstAttempt> firstAttempts(UUID userId, Collection<UUID> questionIds);
    Map<UUID, String> displayNamesByIds(Collection<UUID> userIds);
    void addXp(UUID userId, int delta);
    int currentXp(UUID userId);
    /** Recomputes XP from the canonical first-attempt definition. */
    int xpOf(UUID userId);
    List<UserXpRow> topByXp(int limit);
}
