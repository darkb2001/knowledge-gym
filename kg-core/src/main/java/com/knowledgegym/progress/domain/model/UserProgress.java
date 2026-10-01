package com.knowledgegym.progress.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Per-user, per-module read model derived from study_attempts. */
public record UserProgress(UUID userId, UUID moduleId, BigDecimal masteryPct,
                           int totalAttempts, int correctCount, Instant lastActiveAt) {}
