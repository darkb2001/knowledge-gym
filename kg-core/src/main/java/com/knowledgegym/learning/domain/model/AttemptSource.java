package com.knowledgegym.learning.domain.model;

/** Nguồn phát sinh attempt — phải khớp CHECK constraint `study_attempts.source` (V004). */
public enum AttemptSource {
    FLASHCARD,
    DAILY,
    PRACTICE
}
