package com.knowledgegym.learning.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 1 câu trả lời trong phiên quiz — bảng `quiz_answers` (V013) + UK `(session_id, question_id)` (V016).
 *
 * <p>UK ở DB là hàng rào thật chống double-submit: `insertIgnoringDuplicates` dựa vào
 * `ON CONFLICT DO NOTHING`, nên submit lại không tạo row thứ hai **và** không sửa row cũ (điểm đã
 * chốt không được đổi sau khi user đã thấy kết quả).
 *
 * <p>`selectedOptionId` nullable: câu user bỏ trống vẫn ghi 1 row `is_correct = false` để
 * `study_attempts` phản ánh đúng "đã gặp câu này và sai", thay vì biến mất khỏi lịch sử.
 */
public record QuizAnswer(UUID sessionId,
                         UUID questionId,
                         UUID selectedOptionId,
                         String answerText,
                         boolean correct,
                         Integer timeMs,
                         Instant attemptedAt) {

    public QuizAnswer {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        if (timeMs != null && timeMs < 0) {
            throw new IllegalArgumentException("timeMs không được âm: " + timeMs);
        }
    }
}
