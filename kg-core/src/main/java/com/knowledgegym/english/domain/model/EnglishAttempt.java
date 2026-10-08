package com.knowledgegym.english.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EnglishAttempt(UUID id, UUID userId, String exerciseId, String status, long version,
                             Map<String, Integer> answers, String response, int elapsedSeconds,
                             Instant createdAt, Instant updatedAt) {}
