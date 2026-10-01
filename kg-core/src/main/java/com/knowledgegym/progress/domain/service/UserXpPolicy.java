package com.knowledgegym.progress.domain.service;

import com.knowledgegym.learning.domain.model.AttemptSource;

/** Single XP formula used by the incremental writer and the recomputation query. */
public final class UserXpPolicy {
    private UserXpPolicy() {}

    public static int xpFor(AttemptSource source, boolean correct) {
        if (!correct) return 1;
        return switch (source) {
            case FLASHCARD -> 10;
            case DAILY -> 15;
            case PRACTICE -> 5;
        };
    }
}
