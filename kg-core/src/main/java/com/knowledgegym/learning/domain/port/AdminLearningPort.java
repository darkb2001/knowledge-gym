package com.knowledgegym.learning.domain.port;

import com.knowledgegym.shared.domain.model.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface AdminLearningPort {
    enum Kind { QUIZ, SRS, INTERVIEW, PROGRESS }
    record Entry(UUID id, String label, String status, BigDecimal score, Integer total,
                 LocalDate nextReview, Instant occurredAt) {}
    PageResult<Entry> list(UUID userId, Kind kind, int page, int size);
    void resetCard(UUID actor, UUID userId, UUID cardId, String reason);
}
