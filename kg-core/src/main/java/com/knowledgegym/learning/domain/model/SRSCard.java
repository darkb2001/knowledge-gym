package com.knowledgegym.learning.domain.model;

import com.knowledgegym.learning.domain.service.Sm2Scheduler;
import com.knowledgegym.shared.domain.model.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Thẻ SRS — Aggregate Root. Bảng `srs_cards` (V004), UK `(user_id, question_id)`.
 *
 * <p>Trạng thái lịch ôn (interval/ease/repetitions/nextReview) chỉ đổi qua {@link #applyReview} —
 * nơi duy nhất gọi {@link Sm2Scheduler}. Đặt trong aggregate (không phải use case) để lịch ôn và
 * `lastReviewedAt` luôn đồng bộ: nếu use case set từng field bằng tay, quên 1 field là thẻ kẹt
 * ở ngày cũ mà không test nào bắt được.
 *
 * <p>`deckId` / `sourceNoteId` nullable — thẻ tạo từ custom deck chưa gán deck, và
 * `sourceNoteId` chỉ có khi convert từ note (m08).
 */
public class SRSCard extends BaseEntity {

    private UUID userId;
    private UUID questionId;
    private UUID deckId;
    private UUID sourceNoteId;
    private int intervalDays;
    private double easeFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR;
    private LocalDate nextReview;
    private int repetitions;
    private Instant lastReviewedAt;

    private SRSCard() {
    }

    /** Thẻ mới: đến hạn ngay hôm nay, repetitions 0, ease mặc định 2.5. */
    public static SRSCard newCard(UUID userId, UUID questionId, UUID deckId, UUID sourceNoteId, LocalDate today) {
        SRSCard card = new SRSCard();
        card.userId = Objects.requireNonNull(userId, "userId");
        card.questionId = Objects.requireNonNull(questionId, "questionId");
        card.deckId = deckId;
        card.sourceNoteId = sourceNoteId;
        card.nextReview = Objects.requireNonNull(today, "today");
        return card;
    }

    /**
     * Áp 1 lần ôn theo SM-2 và trả về lịch mới (controller cần để trả response).
     *
     * @param quality   0–3 (Again/Hard/Good/Easy) — validate ở {@link Sm2Scheduler}
     * @param reviewedAt thời điểm ôn, để hàm không đọc đồng hồ hệ thống
     */
    public Sm2Scheduler.Schedule applyReview(int quality, LocalDate today, Instant reviewedAt) {
        Sm2Scheduler.Schedule schedule =
                Sm2Scheduler.next(quality, repetitions, intervalDays, easeFactor, today);
        this.intervalDays = schedule.intervalDays();
        this.easeFactor = schedule.easeFactor();
        this.repetitions = schedule.repetitions();
        this.nextReview = schedule.nextReview();
        this.lastReviewedAt = reviewedAt;
        touch();
        return schedule;
    }

    /** `next_review <= today` — khớp predicate `findByUserIdAndNextReviewLessThanEqual`. */
    public boolean isDueOn(LocalDate today) {
        return !nextReview.isAfter(today);
    }

    public UUID getUserId() { return userId; }
    public UUID getQuestionId() { return questionId; }
    public UUID getDeckId() { return deckId; }
    public void setDeckId(UUID deckId) { this.deckId = deckId; }
    public UUID getSourceNoteId() { return sourceNoteId; }

    /*
     * Setter dưới đây chỉ để adapter dựng lại state từ DB (rehydrate). Thay đổi lịch ôn trong
     * luồng nghiệp vụ phải đi qua `applyReview` — nếu không, `nextReview` và `intervalDays` có
     * thể lệch nhau mà không có gì phát hiện.
     */

    public int getIntervalDays() { return intervalDays; }
    public void setIntervalDays(int intervalDays) { this.intervalDays = intervalDays; }
    public double getEaseFactor() { return easeFactor; }
    public void setEaseFactor(double easeFactor) { this.easeFactor = easeFactor; }
    public LocalDate getNextReview() { return nextReview; }
    public void setNextReview(LocalDate nextReview) { this.nextReview = nextReview; }
    public int getRepetitions() { return repetitions; }
    public void setRepetitions(int repetitions) { this.repetitions = repetitions; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(Instant lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }
}
