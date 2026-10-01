package com.knowledgegym.learning.domain.model;

import com.knowledgegym.shared.domain.model.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 1 lần trả lời câu hỏi — bảng `study_attempts` (V004). Nguồn dữ liệu cho dashboard/progress (m7).
 *
 * <p>Không có factory cho mọi nguồn vì m5 chỉ ghi `FLASHCARD`; `DAILY`/`PRACTICE` để m6/m7 dùng
 * cùng class này. `is_correct` NOT NULL ở DB nên caller phải quyết định — không có giá trị mặc định.
 *
 * <p>`answer`/`score` nullable và m5 để NULL: flashcard self-grade không có đáp án chọn, và `score`
 * là thang điểm quiz (m6). Ghi số giả ở đây sẽ làm analytics m7 hiểu sai dữ liệu.
 */
public class StudyAttempt extends BaseEntity {

    private UUID userId;
    private UUID questionId;
    private AttemptSource source;
    private String answer;
    private boolean correct;
    private BigDecimal score;
    private Integer timeMs;
    private Instant attemptedAt;

    private StudyAttempt() {
    }

    public static StudyAttempt record(UUID userId, UUID questionId, AttemptSource source,
                                      boolean correct, Integer timeMs, Instant attemptedAt) {
        StudyAttempt attempt = new StudyAttempt();
        attempt.userId = Objects.requireNonNull(userId, "userId");
        attempt.questionId = Objects.requireNonNull(questionId, "questionId");
        attempt.source = Objects.requireNonNull(source, "source");
        attempt.correct = correct;
        attempt.timeMs = timeMs;
        attempt.attemptedAt = Objects.requireNonNull(attemptedAt, "attemptedAt");
        return attempt;
    }

    public UUID getUserId() { return userId; }
    public UUID getQuestionId() { return questionId; }
    public AttemptSource getSource() { return source; }
    public void setSource(AttemptSource source) { this.source = source; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Integer getTimeMs() { return timeMs; }
    public void setTimeMs(Integer timeMs) { this.timeMs = timeMs; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}
