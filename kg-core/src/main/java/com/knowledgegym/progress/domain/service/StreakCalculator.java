package com.knowledgegym.progress.domain.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Number of consecutive active calendar days ending today; a missed day resets the streak. */
public final class StreakCalculator {
    private StreakCalculator() {}

    public static int current(Collection<LocalDate> activeDates, LocalDate today) {
        Set<LocalDate> dates = new HashSet<>(activeDates);
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        if (!dates.contains(cursor)) return 0;
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
