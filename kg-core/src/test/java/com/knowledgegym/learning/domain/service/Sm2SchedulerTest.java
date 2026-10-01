package com.knowledgegym.learning.domain.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bảng canonical trong `plans/knowledge-gym/mini-phase-05-srs.md` là contract — test này cố định
 * từng dòng. Sửa scheduler mà không sửa bảng (hoặc ngược lại) sẽ làm test đỏ, đó là mục đích.
 */
class Sm2SchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    // ---------------------------------------------------------------- first review (repetitions 0)

    @Test
    void firstReviewAgainResetsToIntervalOneAndLowersEase() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(0, 0, 0, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(1);
        assertThat(s.repetitions()).isZero();
        assertThat(s.easeFactor()).isEqualTo(2.3);
        assertThat(s.nextReview()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void firstReviewHardIsOneDayNotZeroEvenThoughPrevIntervalIsZero() {
        // prev interval = 0 → nhân 1.2 vẫn ra 0; phải dùng lịch card mới thay vì công thức.
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(1, 0, 0, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(1);
        assertThat(s.repetitions()).isEqualTo(1);
        assertThat(s.easeFactor()).isEqualTo(2.36);
    }

    @Test
    void firstReviewGoodIsOneDayAndKeepsEase() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(2, 0, 0, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(1);
        assertThat(s.repetitions()).isEqualTo(1);
        assertThat(s.easeFactor()).isEqualTo(2.5);
        assertThat(s.nextReview()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void firstReviewEasyJumpsToFourDaysAndRaisesEase() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(3, 0, 0, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(4);
        assertThat(s.repetitions()).isEqualTo(1);
        assertThat(s.easeFactor()).isEqualTo(2.6);
        assertThat(s.nextReview()).isEqualTo(TODAY.plusDays(4));
    }

    /** Again ≠ Hard: cùng là "chưa nhớ" nhưng Again reset repetitions, Hard thì tăng. */
    @Test
    void againDiffersFromHardAtFirstReview() {
        Sm2Scheduler.Schedule again = Sm2Scheduler.next(0, 0, 0, 2.5, TODAY);
        Sm2Scheduler.Schedule hard = Sm2Scheduler.next(1, 0, 0, 2.5, TODAY);

        assertThat(again.intervalDays()).isEqualTo(1);
        assertThat(hard.intervalDays()).isEqualTo(1);
        assertThat(again.repetitions()).as("Again reset về 0").isZero();
        assertThat(hard.repetitions()).as("Hard tăng lên 1").isEqualTo(1);
        assertThat(again.easeFactor()).as("Again phạt nhiều hơn Hard").isLessThan(hard.easeFactor());
    }

    // ---------------------------------------------------------------- mature card

    @Test
    void hardScalesPreviousIntervalByTwelveTenths() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(1, 3, 10, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(12); // ceil(10 × 1.2)
        assertThat(s.repetitions()).isEqualTo(4);
        assertThat(s.easeFactor()).isEqualTo(2.36);
        assertThat(s.nextReview()).isEqualTo(TODAY.plusDays(12));
    }

    @Test
    void goodScalesPreviousIntervalByEase() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(2, 3, 10, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(25); // ceil(10 × 2.5)
        assertThat(s.repetitions()).isEqualTo(4);
        assertThat(s.easeFactor()).isEqualTo(2.5);
    }

    @Test
    void easyAppliesEaseTimesBonusAndRaisesEase() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(3, 3, 10, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(33); // ceil(10 × 2.5 × 1.3)
        assertThat(s.repetitions()).isEqualTo(4);
        assertThat(s.easeFactor()).isEqualTo(2.6);
    }

    /** Interval là số ngày nguyên — 2.5 ngày phải thành 3, làm tròn xuống sẽ hẹn ôn sớm hơn lịch. */
    @Test
    void intervalRoundsUpNotDown() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(2, 2, 5, 2.5, TODAY);

        assertThat(s.intervalDays()).isEqualTo(13); // ceil(12.5)
    }

    @Test
    void againResetsIntervalAndRepetitionsOnMatureCard() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(0, 7, 120, 2.3, TODAY);

        assertThat(s.intervalDays()).isEqualTo(1);
        assertThat(s.repetitions()).isZero();
        assertThat(s.easeFactor()).isEqualTo(2.1);
    }

    // ---------------------------------------------------------------- ease floor

    @Test
    void easeNeverDropsBelowFloor() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(0, 0, 0, 1.3, TODAY);

        assertThat(s.easeFactor()).isEqualTo(Sm2Scheduler.EASE_FLOOR);
    }

    @Test
    void repeatedAgainAtFloorKeepsEaseAtFloor() {
        double ease = Sm2Scheduler.EASE_FLOOR;
        for (int i = 0; i < 5; i++) {
            ease = Sm2Scheduler.next(0, 0, 0, ease, TODAY).easeFactor();
        }
        assertThat(ease).isEqualTo(1.3);
    }

    /** Ease lưu ở `NUMERIC(4,2)` — trả về 4 chữ số thập phân sẽ bị DB làm tròn ở chỗ khác. */
    @Test
    void easeIsRoundedToTwoDecimals() {
        Sm2Scheduler.Schedule s = Sm2Scheduler.next(2, 1, 1, 2.33, TODAY);

        assertThat(s.easeFactor()).isEqualTo(2.33);
    }

    // ---------------------------------------------------------------- validation + derived

    @Test
    void qualityOutsideZeroToThreeIsRejected() {
        assertThatThrownBy(() -> Sm2Scheduler.next(4, 0, 0, 2.5, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quality");
        assertThatThrownBy(() -> Sm2Scheduler.next(-1, 0, 0, 2.5, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isCorrectOnlyForGoodAndEasy() {
        assertThat(Sm2Scheduler.isCorrect(0)).as("Again").isFalse();
        assertThat(Sm2Scheduler.isCorrect(1)).as("Hard").isFalse();
        assertThat(Sm2Scheduler.isCorrect(2)).as("Good").isTrue();
        assertThat(Sm2Scheduler.isCorrect(3)).as("Easy").isTrue();
    }

    @Test
    void isCorrectRejectsOutOfRangeQuality() {
        assertThatThrownBy(() -> Sm2Scheduler.isCorrect(5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
