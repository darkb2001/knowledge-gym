package com.knowledgegym.learning.application;

import com.knowledgegym.learning.application.LearningTestSupport.InMemorySRSCardRepository;
import com.knowledgegym.learning.application.LearningTestSupport.InMemoryStudyAttemptRepository;
import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.learning.domain.model.SRSCard;
import com.knowledgegym.learning.domain.model.StudyAttempt;
import com.knowledgegym.learning.domain.service.Sm2Scheduler;
import com.knowledgegym.shared.application.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Review: SM-2 chạy trên state của thẻ, và **mỗi lần review ghi đúng 1 row `study_attempts`**
 * với `source=FLASHCARD` + `is_correct = quality >= 2`.
 *
 * <p>Điểm quan trọng nhất là attempt: m7 cộng XP/mastery từ bảng này, nên thiếu row ở đây là mất
 * dữ liệu tiến độ mà test SM-2 thuần không phát hiện được.
 */
class ReviewCardUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID QUESTION_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final Instant NOW = Instant.parse("2026-03-10T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemorySRSCardRepository cards;
    private InMemoryStudyAttemptRepository attempts;
    private ReviewCardUseCase useCase;
    private SRSCard card;

    @BeforeEach
    void setUp() {
        cards = new InMemorySRSCardRepository();
        attempts = new InMemoryStudyAttemptRepository();
        useCase = new ReviewCardUseCase(cards, attempts, CLOCK);

        card = SRSCard.newCard(USER_ID, QUESTION_ID, null, null, TODAY);
        cards.store.put(card.getId(), card);
    }

    // ------------------------------------------------------------------ schedule

    @Test
    void reviewAppliesScheduleAndPersistsCard() {
        ReviewCardUseCase.ReviewResult result = useCase.execute(USER_ID, card.getId(),
                Sm2Scheduler.QUALITY_GOOD, 4200);

        assertThat(result.intervalDays()).isEqualTo(1);
        assertThat(result.repetitions()).isEqualTo(1);
        assertThat(result.easeFactor()).isEqualTo(2.5);
        assertThat(result.nextReview()).isEqualTo(TODAY.plusDays(1));
        assertThat(result.correct()).isTrue();

        SRSCard stored = cards.store.get(card.getId());
        assertThat(stored.getNextReview()).isEqualTo(TODAY.plusDays(1));
        assertThat(stored.getLastReviewedAt()).isEqualTo(NOW);
    }

    @Test
    void easyGivesLongerIntervalThanGoodOnFirstReview() {
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_EASY, null);

        assertThat(cards.store.get(card.getId()).getIntervalDays()).isEqualTo(4);
    }

    @Test
    void againResetsRepetitionsAndLowersEase() {
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_GOOD, null);
        card.setNextReview(TODAY);
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_AGAIN, null);

        SRSCard stored = cards.store.get(card.getId());
        assertThat(stored.getRepetitions()).isZero();
        assertThat(stored.getIntervalDays()).isEqualTo(1);
        assertThat(stored.getEaseFactor()).isEqualTo(2.3);
    }

    // ------------------------------------------------------------------ study_attempts

    @Test
    void reviewRecordsFlashcardAttemptWithQualityDerivedCorrectness() {
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_GOOD, 4200);

        assertThat(attempts.store).hasSize(1);
        StudyAttempt attempt = attempts.store.get(0);
        assertThat(attempt.getUserId()).isEqualTo(USER_ID);
        assertThat(attempt.getQuestionId()).isEqualTo(QUESTION_ID);
        assertThat(attempt.getSource()).isEqualTo(AttemptSource.FLASHCARD);
        assertThat(attempt.isCorrect()).as("Good → đúng").isTrue();
        assertThat(attempt.getTimeMs()).isEqualTo(4200);
        assertThat(attempt.getAttemptedAt()).isEqualTo(NOW);
        assertThat(attempt.getAnswer()).as("flashcard tự chấm, không có đáp án chọn").isNull();
        assertThat(attempt.getScore()).as("score là thang điểm quiz (m6), m5 để NULL").isNull();
    }

    @Test
    void eachReviewRecordsItsOwnAttempt() {
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_GOOD, 1000);
        card.setNextReview(TODAY);
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_HARD, 2000);
        card.setNextReview(TODAY);
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_AGAIN, 3000);

        assertThat(attempts.store).hasSize(3);
        assertThat(attempts.store).extracting(StudyAttempt::isCorrect)
                .as("Good=true, Hard=false, Again=false").containsExactly(true, false, false);
        assertThat(attempts.store).extracting(StudyAttempt::getTimeMs).containsExactly(1000, 2000, 3000);
    }

    @Test
    void easyIsAlsoCorrectAndHardIsNot() {
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_EASY, null);
        assertThat(attempts.store.get(0).isCorrect()).isTrue();
    }

    /**
     * `timeMs` không gửi → NULL, KHÔNG default 0: analytics m7 phải phân biệt được "không đo"
     * với "đo được 0 ms" (2 giá trị khác nhau về nghĩa).
     */
    @Test
    void missingTimeMsIsStoredAsNullNotZero() {
        useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_GOOD, null);

        assertThat(attempts.store.get(0).getTimeMs()).isNull();
    }

    // ------------------------------------------------------------------ validation

    @Test
    void unknownQualityIsRejectedBeforeTouchingCardOrAttempts() {
        assertThatThrownBy(() -> useCase.execute(USER_ID, card.getId(), 4, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quality");
        assertThatThrownBy(() -> useCase.execute(USER_ID, card.getId(), -1, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(card.getNextReview()).as("thẻ không đổi").isEqualTo(TODAY);
        assertThat(attempts.store).as("không ghi attempt khi input sai").isEmpty();
    }

    @Test
    void negativeTimeMsIsRejected() {
        assertThatThrownBy(() -> useCase.execute(USER_ID, card.getId(), Sm2Scheduler.QUALITY_GOOD, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeMs");
        assertThat(attempts.store).isEmpty();
    }

    @Test
    void unknownCardReturnsNotFound() {
        assertThatThrownBy(() -> useCase.execute(USER_ID, UUID.randomUUID(), Sm2Scheduler.QUALITY_GOOD, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(attempts.store).isEmpty();
    }

    /**
     * Thẻ của người khác trả 404 (không phải 403): xác nhận 403 là xác nhận id tồn tại, cho phép
     * dò thẻ của user khác bằng cách thử id.
     */
    @Test
    void cardOwnedByAnotherUserReturnsNotFoundAndIsNotModified() {
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(otherUserId, card.getId(), Sm2Scheduler.QUALITY_GOOD, null))
                .isInstanceOf(NotFoundException.class);

        assertThat(cards.store.get(card.getId()).getNextReview()).isEqualTo(TODAY);
        assertThat(attempts.store).isEmpty();
    }
}
