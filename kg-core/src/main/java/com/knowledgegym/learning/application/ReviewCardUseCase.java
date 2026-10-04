package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.learning.domain.model.SRSCard;
import com.knowledgegym.learning.domain.port.SRSCardRepository;
import com.knowledgegym.progress.application.RecordAttemptUseCase;
import com.knowledgegym.learning.domain.service.Sm2Scheduler;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ghi kết quả 1 lần ôn flashcard: chạy SM-2, cập nhật thẻ, và ghi `study_attempts` — **cùng một
 * transaction**.
 *
 * <p>Lý do attempt phải nằm trong cùng tx (quyết định kiến trúc của m5): m7 cộng XP/mastery từ
 * `study_attempts`, nên nếu chỉ cập nhật thẻ mà insert attempt thất bại, người học mất tiến độ
 * "đã ôn" mà lịch ôn vẫn nhảy — sai lệch âm thầm không có gì sửa lại được.
 *
 * <p>`is_correct = quality >= 2` (Good/Easy) là quy ước của m5, suy ra từ quality chứ không nhận
 * từ client: schema bắt `is_correct NOT NULL` và không có trường nào khác để suy ra. `timeMs`
 * nullable — FE không đo thì lưu NULL, **không** default số giả (analytics m7 phải phân biệt được
 * "không đo" với "đo được 0ms").
 *
 * <p>Thẻ được đọc kèm khoá bi quan (`findByIdForUpdate`): `srs_cards` không có cột `version`, nên
 * hai request review đồng thời cùng thẻ sẽ mất một lần cập nhật lịch (last-write-wins) — số lần
 * ôn (2 row attempt) và lịch ôn (1 lần nhảy) lệch nhau. Khoá làm lần review thứ hai nối tiếp lần
 * thứ nhất, nên nó thấy `repetitions` đã tăng và SM-2 tính đúng.
 */
public class ReviewCardUseCase {

    private final SRSCardRepository cardRepository;
    private final RecordAttemptUseCase recordAttemptUseCase;
    private final Clock clock;
    private final QuestionRepository questions;

    public ReviewCardUseCase(SRSCardRepository cardRepository, RecordAttemptUseCase recordAttemptUseCase,
                             Clock clock, QuestionRepository questions) {
        this.cardRepository = cardRepository;
        this.recordAttemptUseCase = recordAttemptUseCase;
        this.clock = clock;
        this.questions = questions;
    }

    public record ReviewResult(int quality, int intervalDays, double easeFactor, int repetitions,
                               LocalDate nextReview, boolean correct) {
    }

    @Transactional
    public ReviewResult execute(UUID userId, UUID cardId, int quality, Integer timeMs) {
        if (quality < Sm2Scheduler.QUALITY_AGAIN || quality > Sm2Scheduler.QUALITY_EASY) {
            throw new IllegalArgumentException("quality phải trong 0..3: " + quality);
        }
        if (timeMs != null && timeMs < 0) {
            throw new IllegalArgumentException("timeMs không được âm: " + timeMs);
        }

        SRSCard card = cardRepository.findByIdForUpdate(cardId, userId)
                // Thẻ của người khác trả 404 (không phải 403): không xác nhận sự tồn tại của id.
                .orElseThrow(() -> new NotFoundException("Thẻ SRS không tồn tại: " + cardId));

        // A stale browser tab must not resume withdrawn content or change its schedule/XP.
        questions.findById(card.getQuestionId())
                .filter(question -> question.getContentStatus() == Question.ContentStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Nội dung thẻ SRS không còn khả dụng"));

        Instant now = Instant.now(clock);
        Sm2Scheduler.Schedule schedule = card.applyReview(quality, LocalDate.now(clock), now);
        cardRepository.save(card);

        boolean correct = Sm2Scheduler.isCorrect(quality);
        recordAttemptUseCase.execute(userId, List.of(new RecordAttemptUseCase.AttemptFact(
                card.getQuestionId(), AttemptSource.FLASHCARD, correct, timeMs, null, null)), now);

        return new ReviewResult(quality, schedule.intervalDays(), schedule.easeFactor(),
                schedule.repetitions(), schedule.nextReview(), correct);
    }
}
