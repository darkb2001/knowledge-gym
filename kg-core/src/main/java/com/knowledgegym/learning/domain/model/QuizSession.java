package com.knowledgegym.learning.domain.model;

import com.knowledgegym.shared.domain.model.BaseEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Phiên quiz — aggregate root, bảng `quiz_sessions` (V013) + membership ở `quiz_session_questions`
 * (V016).
 *
 * <p><b>Vì sao membership nằm trong aggregate:</b> "phiên gồm câu nào" và "điểm của phiên" phải đọc
 * ra cùng một sự thật. Nếu chỉ lưu `total` (số câu) thì submit có thể chấm trên tập câu khác với tập
 * đã phát cho user, và không có gì phát hiện — `QuizSession` giữ `questionIds` nên mọi phép chấm đều
 * đối chiếu được với tập đã giao.
 *
 * <p><b>`score` là phần trăm</b>, không phải số câu đúng — `quiz_sessions.score` là INT và UI hiển
 * thị "%". Số câu đúng suy từ `quiz_answers`, không lưu trùng ở đây (2 nguồn cho cùng con số là 2
 * nguồn có thể lệch nhau).
 */
public class QuizSession extends BaseEntity {

    private UUID userId;
    private QuizStrategy strategy;
    private Integer score;
    private Integer total;
    private Instant startedAt;
    private Instant finishedAt;
    private List<UUID> questionIds = List.of();

    private QuizSession() {
    }

    /**
     * Mở phiên mới. `total` chốt ngay lúc tạo từ số câu đã chọn — phiên đã giao câu nào thì chấm
     * đúng trên tập đó, không phụ thuộc dữ liệu thay đổi sau này.
     */
    public static QuizSession start(UUID userId, QuizStrategy strategy, List<UUID> questionIds,
                                    Instant startedAt) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(startedAt, "startedAt");
        if (questionIds == null || questionIds.isEmpty()) {
            throw new IllegalArgumentException("Phiên quiz phải có ít nhất 1 câu hỏi");
        }
        if (questionIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("questionIds không được chứa phần tử null");
        }
        if (questionIds.size() != List.copyOf(new java.util.LinkedHashSet<>(questionIds)).size()) {
            throw new IllegalArgumentException("questionIds không được trùng trong 1 phiên");
        }
        QuizSession session = new QuizSession();
        session.userId = userId;
        session.strategy = strategy;
        session.questionIds = List.copyOf(questionIds);
        session.total = session.questionIds.size();
        session.startedAt = startedAt;
        return session;
    }

    /** Dựng lại từ DB — membership đọc từ `quiz_session_questions` nên phải truyền vào. */
    public static QuizSession rehydrate(UUID id, UUID userId, QuizStrategy strategy, Integer score,
                                        Integer total, Instant startedAt, Instant finishedAt,
                                        List<UUID> questionIds) {
        QuizSession session = new QuizSession();
        session.setId(Objects.requireNonNull(id, "id"));
        session.userId = Objects.requireNonNull(userId, "userId");
        session.strategy = Objects.requireNonNull(strategy, "strategy");
        session.score = score;
        session.total = total;
        session.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        session.finishedAt = finishedAt;
        session.questionIds = questionIds == null ? List.of() : List.copyOf(questionIds);
        return session;
    }

    /**
     * Chốt kết quả. `correctCount` là số câu đúng, `total` lấy từ membership (không nhận từ caller:
     * caller có thể truyền số khác và tạo ra điểm không cộng lại được).
     *
     * @throws IllegalStateException nếu phiên đã finish — submit lần hai phải bị chặn ở tầng DB
     *                               (UK) và use case, không được ghi đè điểm cũ.
     */
    public int finish(int correctCount, Instant finishedAt) {
        if (isFinished()) {
            throw new IllegalStateException("Phiên quiz đã kết thúc: " + getId());
        }
        if (correctCount < 0 || correctCount > questionIds.size()) {
            throw new IllegalArgumentException(
                    "correctCount ngoài khoảng 0.." + questionIds.size() + ": " + correctCount);
        }
        Objects.requireNonNull(finishedAt, "finishedAt");
        this.score = percentage(correctCount, questionIds.size());
        this.finishedAt = finishedAt;
        touch();
        return this.score;
    }

    /** `round(correct/total*100)`; total 0 không xảy ra (factory chặn) nhưng vẫn phòng. */
    static int percentage(int correctCount, int total) {
        if (total <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(correctCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

    /** Câu này có thuộc phiên không — dùng để từ chối answer cho câu không được giao. */
    public boolean containsQuestion(UUID questionId) {
        return questionIds.contains(questionId);
    }

    public UUID getUserId() { return userId; }
    public QuizStrategy getStrategy() { return strategy; }
    public Integer getScore() { return score; }
    public Integer getTotal() { return total; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public List<UUID> getQuestionIds() { return questionIds; }
}
