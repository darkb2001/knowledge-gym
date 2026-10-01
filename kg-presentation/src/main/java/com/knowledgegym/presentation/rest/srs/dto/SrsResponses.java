package com.knowledgegym.presentation.rest.srs.dto;

import com.knowledgegym.learning.application.EnrollCardsUseCase;
import com.knowledgegym.learning.application.ReviewCardUseCase;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response shapes cho `/srs/*` — tách khỏi domain model để đổi nội bộ không phá client. */
public final class SrsResponses {

    private SrsResponses() {
    }

    /**
     * `deckId` null khi mode B không gửi deck, hoặc khi module chưa có câu hỏi (no-op thật sự —
     * không tạo deck rỗng).
     */
    public record EnrollResponse(int enrolled, List<UUID> cardIds, UUID deckId) {

        public static EnrollResponse of(EnrollCardsUseCase.EnrollResult result) {
            return new EnrollResponse(result.enrolled(), result.cardIds(), result.deckId());
        }
    }

    /** `correct` là diễn giải `quality >= 2` để FE không phải hardcode lại ngưỡng. */
    public record ReviewResponse(UUID cardId, int quality, int intervalDays, double easeFactor,
                                 int repetitions, LocalDate nextReview, boolean correct) {

        public static ReviewResponse of(UUID cardId, ReviewCardUseCase.ReviewResult result) {
            return new ReviewResponse(cardId, result.quality(), result.intervalDays(),
                    result.easeFactor(), result.repetitions(), result.nextReview(), result.correct());
        }
    }
}
