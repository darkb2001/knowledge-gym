package com.knowledgegym.notes.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Note(
        UUID id,
        UUID userId,
        UUID questionId,
        UUID moduleId,
        String noteType,
        String content,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}
