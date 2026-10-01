package com.knowledgegym.progress.domain.service;

import com.knowledgegym.learning.domain.model.AttemptSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressDomainServicesTest {

    @Test
    void xpFormulaIsTheSingleDefinitionForEverySourceAndOutcome() {
        assertThat(UserXpPolicy.xpFor(AttemptSource.FLASHCARD, true)).isEqualTo(10);
        assertThat(UserXpPolicy.xpFor(AttemptSource.DAILY, true)).isEqualTo(15);
        assertThat(UserXpPolicy.xpFor(AttemptSource.PRACTICE, true)).isEqualTo(5);
    }

    /** Sai luôn 1 XP, không phụ thuộc nguồn — giữ đơn giản để recompute khớp đường ghi. */
    @ParameterizedTest
    @EnumSource(AttemptSource.class)
    void wrongAnswerAlwaysAwardsExactlyOneXp(AttemptSource source) {
        assertThat(UserXpPolicy.xpFor(source, false)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(AttemptSource.class)
    void correctAnswerNeverAwardsLessThanAWrongOne(AttemptSource source) {
        assertThat(UserXpPolicy.xpFor(source, true))
                .isGreaterThanOrEqualTo(UserXpPolicy.xpFor(source, false));
    }

    @ParameterizedTest
    @CsvSource({
            "0,    0,   0.00",
            "1,    0,   0.00",
            "1,    1, 100.00",
            "2,    1,  50.00",
            "3,    1,  33.33",
            "3,    2,  66.67",
            "7,    3,  42.86",
    })
    void masteryRoundsHalfUpToTwoDecimals(int total, int correct, String expected) {
        assertThat(MasteryCalculator.percentage(correct, total))
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void masteryHasTwoDecimalsEvenWhenZero() {
        assertThat(MasteryCalculator.percentage(0, 0))
                .as("scale cố định để NUMERIC(5,2) không ghi ra 0 thay vì 0.00")
                .isEqualByComparingTo("0.00");
        assertThat(MasteryCalculator.percentage(0, 0).scale()).isEqualTo(2);
    }

    @Test
    void streakCountsConsecutiveDaysEndingToday() {
        assertThat(StreakCalculator.current(java.util.List.of(
                java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 9, 30),
                java.time.LocalDate.of(2026, 9, 29)), java.time.LocalDate.of(2026, 10, 1)))
                .isEqualTo(3);
    }

    /**
     * Streak được tính theo ngày, nên học nhiều lần trong cùng một ngày là **một** ngày hoạt động —
     * nguồn là `SELECT DISTINCT (attempted_at AT TIME ZONE zone)::date`.
     */
    @Test
    void repeatedActivityOnTheSameDayCountsOnce() {
        assertThat(StreakCalculator.current(java.util.List.of(
                java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 10, 1)), java.time.LocalDate.of(2026, 10, 1)))
                .isEqualTo(1);
    }

    @Test
    void missingYesterdayResetsTheStreak() {
        assertThat(StreakCalculator.current(java.util.List.of(
                java.time.LocalDate.of(2026, 9, 28),
                java.time.LocalDate.of(2026, 9, 27)), java.time.LocalDate.of(2026, 10, 1)))
                .isZero();
    }

    @Test
    void activityTodayButNotYesterdayGivesAStreakOfOne() {
        assertThat(StreakCalculator.current(java.util.List.of(java.time.LocalDate.of(2026, 10, 1)),
                java.time.LocalDate.of(2026, 10, 1))).isEqualTo(1);
    }

    /**
     * Chưa học hôm nay nhưng học hôm qua thì streak **vẫn còn** (chưa bị reset giữa ngày) — cùng
     * quy ước với `current` bắt đầu từ hôm qua khi hôm nay không có hoạt động.
     */
    @Test
    void streakSurvivesTheCurrentDayUntilItEnds() {
        assertThat(StreakCalculator.current(java.util.List.of(
                java.time.LocalDate.of(2026, 9, 30),
                java.time.LocalDate.of(2026, 9, 29)), java.time.LocalDate.of(2026, 10, 1)))
                .isEqualTo(2);
    }

    @Test
    void noActivityAtAllIsAZeroStreak() {
        assertThat(StreakCalculator.current(java.util.List.of(), java.time.LocalDate.of(2026, 10, 1)))
                .isZero();
    }

    /** Nhảy cách quãng: chuỗi dừng ở chỗ thủng đầu tiên tính từ hôm nay/hôm qua. */
    @Test
    void gapStopsTheCountAtTheFirstMissingDay() {
        assertThat(StreakCalculator.current(java.util.List.of(
                java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 9, 30),
                java.time.LocalDate.of(2026, 9, 27)), java.time.LocalDate.of(2026, 10, 1)))
                .isEqualTo(2);
    }
}
