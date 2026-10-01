package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.learning.domain.model.QuizAnswer;
import com.knowledgegym.learning.domain.model.QuizSession;
import com.knowledgegym.learning.domain.port.QuizSessionRepository;
import com.knowledgegym.learning.domain.port.StudyAttemptRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.port.DistributedLockPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nộp bài quiz: chấm điểm, lưu `quiz_answers`, chốt `quiz_sessions`, và ghi `study_attempts` — tất
 * cả trong **một** transaction.
 *
 * <p><b>Vì sao cùng transaction (P6):</b> m7 cộng XP/radar từ `study_attempts` và **không** aggregate
 * `quiz_answers`. Nếu chấm điểm thành công mà insert attempt thất bại, dashboard vĩnh viễn không thấy
 * hoạt động quiz này — sai lệch âm thầm không sửa lại được. Ngược lại, attempt phải rollback cùng
 * điểm.
 *
 * <p><b>Chống double-submit 3 lớp, mỗi lớp một vai trò:</b>
 * <ol>
 *   <li>Redis lock — **debounce**: hai request bấm đúp trong vài giây chỉ một cái vào DB. Fail-open,
 *       không phải guard tính đúng đắn.</li>
 *   <li>Khoá row (`findByIdAndUserIdForUpdate`) — hai request đồng thời được nối tiếp nhau, request
 *       sau thấy `finished_at != NULL`.</li>
 *   <li>UK `(session_id, question_id)` — hàng rào cuối ở DB, đúng cả khi Redis chết và lock row bị
 *       bỏ qua vì lý do nào đó.</li>
 * </ol>
 *
 * <p><b>Câu bỏ trống tính là sai:</b> `total` là số câu đã giao lúc tạo phiên, không phải số phần tử
 * client gửi lên. Nếu lấy số gửi lên làm mẫu số, user bỏ 8/10 câu sẽ được 100% cho 2 câu đúng — phần
 * thưởng cho việc bỏ bài.
 */
public class SubmitQuizUseCase {

    /** TTL chỉ cần phủ một lần bấm đúp; UK ở DB mới là guard thật nên TTL ngắn là đủ và an toàn. */
    private static final Duration DEBOUNCE_TTL = Duration.ofSeconds(10);

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final StudyAttemptRepository attemptRepository;
    private final DistributedLockPort lock;
    private final Clock clock;

    public SubmitQuizUseCase(QuizSessionRepository sessionRepository,
                             QuestionRepository questionRepository,
                             QuestionOptionRepository optionRepository,
                             StudyAttemptRepository attemptRepository,
                             DistributedLockPort lock,
                             Clock clock) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.attemptRepository = attemptRepository;
        this.lock = lock;
        this.clock = clock;
    }

    public record SubmitCommand(List<SubmittedAnswer> answers) {
    }

    /** `selectedOptionId` null = để trống; vẫn được ghi nhận là câu sai. */
    public record SubmittedAnswer(UUID questionId, UUID selectedOptionId, Integer timeMs) {
    }

    /** 1 dòng breakdown — FE tô đúng/sai và chỉ ra đáp án đúng. */
    public record AnswerBreakdown(UUID questionId, boolean correct, UUID selectedOptionId,
                                  UUID correctOptionId) {
    }

    public record QuizResult(UUID sessionId, int score, int correctCount, int total,
                             List<AnswerBreakdown> breakdown) {
    }

    @Transactional
    public QuizResult execute(UUID userId, UUID sessionId, SubmitCommand command) {
        List<SubmittedAnswer> answers = validate(command);

        // Debounce fail-open: Redis chết thì `tryAcquire` trả true và luồng đi tiếp — tính đúng đắn
        // do 2 lớp sau giữ, không phải lớp này.
        String lockKey = "lock:quiz:" + sessionId + ":" + userId;
        String lockToken = UUID.randomUUID().toString();
        if (!lock.tryAcquire(lockKey, lockToken, DEBOUNCE_TTL)) {
            throw new ConflictException("Bài quiz đang được nộp, thử lại sau");
        }
        try {
            return grade(userId, sessionId, answers);
        } finally {
            lock.release(lockKey, lockToken);
        }
    }

    private QuizResult grade(UUID userId, UUID sessionId, List<SubmittedAnswer> answers) {
        QuizSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Phiên quiz không tồn tại: " + sessionId));
        if (session.isFinished()) {
            throw new ConflictException("Phiên quiz đã được nộp: " + sessionId);
        }
        if (session.getQuestionIds().size() != session.getTotal()) throw new ConflictException("Nội dung phiên quiz đã thay đổi");
        for (SubmittedAnswer answer : answers) {
            if (!session.containsQuestion(answer.questionId())) {
                throw new IllegalArgumentException(
                        "Câu hỏi không thuộc phiên quiz này: " + answer.questionId());
            }
        }

        if (answers.size() > session.getTotal()) throw new IllegalArgumentException("Quá nhiều answers");
        Map<UUID, List<QuestionOption>> available = optionRepository.findByQuestionIds(session.getQuestionIds());
        for (SubmittedAnswer answer : answers) {
            if (answer.selectedOptionId() != null && available.getOrDefault(answer.questionId(), List.of()).stream()
                    .noneMatch(o -> answer.selectedOptionId().equals(o.getId())))
                throw new IllegalArgumentException("Option không thuộc câu hỏi");
        }
        Map<UUID, SubmittedAnswer> submitted = answers.stream().collect(Collectors.toMap(SubmittedAnswer::questionId, Function.identity()));
        answers = session.getQuestionIds().stream().map(id -> submitted.getOrDefault(id, new SubmittedAnswer(id, null, null))).toList();
        List<UUID> questionIds = session.getQuestionIds();
        Map<UUID, QuestionOption> correctOptionByQuestion = correctOptionsOf(available, questionIds);

        Instant now = Instant.now(clock);
        List<QuizAnswer> toInsert = new ArrayList<>(answers.size());
        List<AnswerBreakdown> breakdown = new ArrayList<>(answers.size());
        int correctCount = 0;

        for (SubmittedAnswer answer : answers) {
            QuestionOption correctOption = correctOptionByQuestion.get(answer.questionId());
            UUID correctOptionId = correctOption == null ? null : correctOption.getId();
            boolean correct = correctOptionId != null && correctOptionId.equals(answer.selectedOptionId());
            if (correct) {
                correctCount++;
            }
            // Snapshot nội dung option đã chọn: re-import sửa `question_options` sẽ set
            // `selected_option_id` về NULL (FK ON DELETE SET NULL), nhưng `answer_text` giữ lại được
            // user đã chọn gì. Không có nó thì lịch sử quiz cũ mất thông tin không khôi phục được.
            String answerText = answer.selectedOptionId() == null ? null
                    : available.getOrDefault(answer.questionId(), List.of()).stream()
                            .filter(option -> option.getId().equals(answer.selectedOptionId()))
                            .map(QuestionOption::getContent).findFirst().orElse(null);
            toInsert.add(new QuizAnswer(sessionId, answer.questionId(), answer.selectedOptionId(),
                    answerText, correct, answer.timeMs(), now));
            breakdown.add(new AnswerBreakdown(answer.questionId(), correct, answer.selectedOptionId(),
                    correctOptionId));
        }

        int inserted = sessionRepository.insertAnswersIgnoringDuplicates(toInsert);
        if (inserted < toInsert.size()) {
            // 2 lớp trước đã cho qua mà DB vẫn thấy row trùng: chỉ xảy ra khi có submit song song
            // vượt qua lock (Redis chết + tranh chấp row). Ném để rollback — điểm không được chốt
            // trên một tập answer không đầy đủ.
            throw new ConflictException("Phiên quiz đã được nộp: " + sessionId);
        }

        int score = session.finish(correctCount, now);
        sessionRepository.save(session);

        for (QuizAnswer answer : toInsert) {
            var attempt = com.knowledgegym.learning.domain.model.StudyAttempt.record(
                    userId, answer.questionId(), AttemptSource.PRACTICE, answer.correct(), answer.timeMs(), now);
            attempt.setScore(BigDecimal.valueOf(answer.correct() ? 100 : 0));
            attempt.setAnswer(answer.selectedOptionId() == null ? null : answer.selectedOptionId().toString());
            attemptRepository.save(attempt);
        }

        // Breakdown luôn theo thứ tự câu của session, gồm cả câu bỏ trống.
        return new QuizResult(sessionId, score, correctCount, session.getTotal(),
                List.copyOf(breakdown));
    }

    /**
     * Chuẩn hoá + chặn input sai: phần tử null (bài học m5 — Jackson deserialize `[null]` được, rồi
     * `List.copyOf` ném NPE → 500 thay vì 400), trùng `questionId` (hai dòng cùng câu làm câu lệnh
     * `ON CONFLICT` của Postgres nổ), và vượt số câu của phiên.
     */
    private List<SubmittedAnswer> validate(SubmitCommand command) {
        if (command == null || command.answers() == null) {
            throw new IllegalArgumentException("answers không được để trống");
        }
        if (command.answers().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("answers không được chứa phần tử null");
        }
        Set<UUID> seen = new HashSet<>();
        for (SubmittedAnswer answer : command.answers()) {
            if (answer.questionId() == null) {
                throw new IllegalArgumentException("answers.questionId không được để trống");
            }
            if (!seen.add(answer.questionId())) {
                throw new IllegalArgumentException("answers chứa câu hỏi trùng: " + answer.questionId());
            }
            if (answer.timeMs() != null && answer.timeMs() < 0) {
                throw new IllegalArgumentException("timeMs không được âm: " + answer.timeMs());
            }
        }
        return List.copyOf(command.answers());
    }

    /** Đáp án đúng của từng câu trong phiên — 1 query cho cả phiên, không N+1. */
    private Map<UUID, QuestionOption> correctOptionsOf(Map<UUID, List<QuestionOption>> byQuestion, List<UUID> questionIds) {
        Map<UUID, QuestionOption> result = new LinkedHashMap<>();
        for (UUID questionId : questionIds) {
            List<QuestionOption> options = byQuestion.getOrDefault(questionId, List.of());
            options.stream().filter(QuestionOption::isCorrect).findFirst()
                    .ifPresent(option -> result.put(questionId, option));
        }
        return result;
    }
}
