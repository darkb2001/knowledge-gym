package com.knowledgegym.learning.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * SM-2 scheduler — domain service thuần Java (không Spring/JPA, ArchUnit `DomainLayerArchTest` enforce).
 *
 * <p><b>Contract (canonical):</b> API quality của FE là thang 0–3 (Again/Hard/Good/Easy), KHÔNG phải
 * thang SM-2 gốc 0–5. Bảng dưới đây là source of truth — override mọi mô tả mapping ở docs khác
 * (kể cả snippet minh hoạ trong `docs/06-feature-knowledge-mapping.md`).
 *
 * <table>
 *   <caption>Mapping quality → hành vi</caption>
 *   <tr><th>API</th><th>Nghĩa</th><th>SM-2 q</th><th>Hành vi</th></tr>
 *   <tr><td>0</td><td>Again</td><td>0–2</td><td>repetitions = 0, interval = 1, ease − 0.2</td></tr>
 *   <tr><td>1</td><td>Hard</td><td>3</td><td>interval = ceil(prev × 1.2), ease − 0.14, repetitions + 1</td></tr>
 *   <tr><td>2</td><td>Good</td><td>4</td><td>interval = ceil(prev × ease), ease giữ nguyên, repetitions + 1</td></tr>
 *   <tr><td>3</td><td>Easy</td><td>5</td><td>interval = ceil(prev × ease × 1.3), ease + 0.1, repetitions + 1</td></tr>
 * </table>
 *
 * <p>Lần ôn đầu tiên (`repetitions == 0`) dùng lịch riêng của Anki cho thẻ mới — Again/Hard/Good
 * đều 1 ngày, Easy 4 ngày — vì công thức nhân với `interval = 0` sẽ cho ra 0.
 *
 * <p>Ease bị chặn dưới ở {@link #EASE_FLOOR} và làm tròn 2 chữ số thập phân để khớp cột
 * `srs_cards.ease_factor NUMERIC(4,2)` — nếu không, giá trị double lệch sẽ bị DB làm tròn ở chỗ khác
 * và kết quả tính toán sau đó không tái lập được.
 */
public final class Sm2Scheduler {

    public static final double EASE_FLOOR = 1.3;
    public static final double DEFAULT_EASE_FACTOR = 2.5;

    public static final int QUALITY_AGAIN = 0;
    public static final int QUALITY_HARD = 1;
    public static final int QUALITY_GOOD = 2;
    public static final int QUALITY_EASY = 3;

    /** Thẻ "nhớ được" từ Good trở lên — dùng để suy ra `study_attempts.is_correct`. */
    public static final int CORRECT_THRESHOLD = QUALITY_GOOD;

    private static final int FIRST_REVIEW_INTERVAL_DAYS = 1;
    private static final int FIRST_REVIEW_EASY_INTERVAL_DAYS = 4;
    private static final double HARD_FACTOR = 1.2;
    private static final double EASY_BONUS_FACTOR = 1.3;
    private static final double AGAIN_EASE_PENALTY = 0.2;
    private static final double HARD_EASE_PENALTY = 0.14;
    private static final double EASY_EASE_BONUS = 0.1;

    private Sm2Scheduler() {
    }

    /** Trạng thái thẻ sau 1 lần ôn. `nextReview` = `today + intervalDays`. */
    public record Schedule(int intervalDays, double easeFactor, int repetitions, LocalDate nextReview) {
    }

    /**
     * @param quality      0–3 (xem bảng ở class javadoc); ngoài khoảng → {@link IllegalArgumentException}
     * @param repetitions  số lần nhớ liên tiếp của thẻ
     * @param intervalDays interval hiện tại (ngày); thẻ mới = 0
     * @param easeFactor   ease hiện tại
     * @param today        ngày ôn — truyền vào để hàm thuần, không đọc đồng hồ hệ thống
     */
    public static Schedule next(int quality, int repetitions, int intervalDays, double easeFactor, LocalDate today) {
        if (quality < QUALITY_AGAIN || quality > QUALITY_EASY) {
            throw new IllegalArgumentException("quality phải trong 0..3: " + quality);
        }
        if (repetitions < 0) {
            throw new IllegalArgumentException("repetitions không được âm: " + repetitions);
        }
        if (intervalDays < 0) {
            throw new IllegalArgumentException("intervalDays không được âm: " + intervalDays);
        }

        int newRepetitions;
        int newIntervalDays;
        double newEaseFactor = easeFactor;

        switch (quality) {
            case QUALITY_AGAIN -> {
                newRepetitions = 0;
                newIntervalDays = FIRST_REVIEW_INTERVAL_DAYS;
                newEaseFactor = clampEase(easeFactor - AGAIN_EASE_PENALTY);
            }
            case QUALITY_HARD -> {
                newRepetitions = repetitions + 1;
                newIntervalDays = repetitions == 0
                        ? FIRST_REVIEW_INTERVAL_DAYS
                        : scaleInterval(intervalDays, HARD_FACTOR);
                newEaseFactor = clampEase(easeFactor - HARD_EASE_PENALTY);
            }
            case QUALITY_EASY -> {
                newRepetitions = repetitions + 1;
                newIntervalDays = repetitions == 0
                        ? FIRST_REVIEW_EASY_INTERVAL_DAYS
                        : scaleInterval(intervalDays, easeFactor * EASY_BONUS_FACTOR);
                newEaseFactor = clampEase(easeFactor + EASY_EASE_BONUS);
            }
            default -> {
                newRepetitions = repetitions + 1;
                newIntervalDays = repetitions == 0
                        ? FIRST_REVIEW_INTERVAL_DAYS
                        : scaleInterval(intervalDays, easeFactor);
            }
        }

        return new Schedule(newIntervalDays, roundEase(newEaseFactor), newRepetitions,
                today.plusDays(newIntervalDays));
    }

    /** `is_correct` của `study_attempts` — Again/Hard = false, Good/Easy = true. */
    public static boolean isCorrect(int quality) {
        if (quality < QUALITY_AGAIN || quality > QUALITY_EASY) {
            throw new IllegalArgumentException("quality phải trong 0..3: " + quality);
        }
        return quality >= CORRECT_THRESHOLD;
    }

    /** Làm tròn lên: interval là số ngày nguyên, 2.5 ngày nghĩa là 3 ngày chứ không phải 2. */
    private static int scaleInterval(int intervalDays, double factor) {
        return (int) Math.ceil(intervalDays * factor);
    }

    private static double clampEase(double ease) {
        return Math.max(EASE_FLOOR, ease);
    }

    private static double roundEase(double ease) {
        return BigDecimal.valueOf(ease).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
