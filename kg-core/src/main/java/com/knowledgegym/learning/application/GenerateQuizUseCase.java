package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.learning.application.strategy.QuizGenerationStrategy;
import com.knowledgegym.learning.domain.model.QuizSession;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.port.QuizSessionRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.domain.model.Difficulty;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Sinh 1 phiên quiz: chọn câu theo strategy → lọc câu có đáp án MCQ hợp lệ → lưu session.
 *
 * <p><b>Chỉ câu có option hợp lệ mới vào phiên.</b> `question_options` được sinh lúc import/backfill
 * (xem `DistractorGenerator`); câu không đủ option (module quá ít câu anh em) bị loại thay vì đưa vào
 * phiên rồi để FE render câu không bấm được. Hệ quả: `count` là **trần**, không phải cam kết — phiên
 * có thể ít câu hơn nếu nội dung chưa đủ, và điều đó được trả về qua `total` thật của session.
 *
 * <p>Không có câu nào hợp lệ → {@link ConflictException} (409): request đúng định dạng nhưng xung đột
 * với trạng thái dữ liệu hiện tại (admin chưa chạy backfill). Trả 200 với phiên rỗng sẽ tạo ra session
 * không chấm được, che mất sự cố nội dung.
 */
public class GenerateQuizUseCase {

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 50;

    /** Gợi ý thời gian cho FE (không có cột DB — xem `docs/08-rest-api.md`). */
    public static final int SECONDS_PER_QUESTION = 60;

    /** Option hợp lệ: phải có ít nhất 2 lựa chọn và **đúng 1** đáp án đúng, nếu không không chấm được. */
    private static final int MIN_OPTIONS = 2;

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final QuizSessionRepository sessionRepository;
    private final Map<QuizStrategy, QuizGenerationStrategy> strategies;
    private final RandomGenerator random;
    private final Clock clock;

    public GenerateQuizUseCase(QuestionRepository questionRepository,
                               QuestionOptionRepository optionRepository,
                               QuizSessionRepository sessionRepository,
                               Map<QuizStrategy, QuizGenerationStrategy> strategies,
                               RandomGenerator random,
                               Clock clock) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.sessionRepository = sessionRepository;
        this.strategies = strategies;
        this.random = random;
        this.clock = clock;
    }

    public record GenerateCommand(UUID moduleId, int count, QuizStrategy strategy, Difficulty difficulty) {
    }

    /** Câu hỏi kèm option đã sắp — `correct` bị bỏ ở DTO public, chỉ use case/submit thấy. */
    public record QuizQuestion(Question question, List<QuestionOption> options) {
    }

    public record GeneratedQuiz(QuizSession session, List<QuizQuestion> questions) {

        public int timeLimitSeconds() {
            return questions.size() * SECONDS_PER_QUESTION;
        }
    }

    @Transactional
    public GeneratedQuiz execute(UUID userId, GenerateCommand command) {
        if (command == null || command.moduleId() == null || command.strategy() == null) {
            throw new IllegalArgumentException("moduleId và strategy bắt buộc");
        }
        if (command.count() < MIN_COUNT || command.count() > MAX_COUNT) {
            throw new IllegalArgumentException(
                    "count phải trong " + MIN_COUNT + ".." + MAX_COUNT + ": " + command.count());
        }
        QuizGenerationStrategy strategy = strategies.get(command.strategy());
        if (strategy == null) {
            // Không xảy ra với enum hợp lệ; giữ nhánh này để lỗi cấu hình bean hiện ra rõ ràng thay
            // vì NPE ở dòng dưới.
            throw new IllegalStateException("Chưa đăng ký strategy: " + command.strategy());
        }

        List<UUID> ranked = strategy.rank(userId, command.moduleId(), command.difficulty(), random);
        if (ranked.isEmpty()) {
            throw new ConflictException("Không có câu hỏi phù hợp với chiến lược và bộ lọc hiện tại");
        }

        // 1 query cho toàn bộ pool xếp hạng: gọi theo từng câu là N+1 và pool có thể tới hàng trăm.
        Map<UUID, List<QuestionOption>> optionsByQuestion =
                optionRepository.findByQuestionIds(ranked);

        List<UUID> selectedIds = new ArrayList<>(command.count());
        Set<UUID> selected = new LinkedHashSet<>();
        for (UUID questionId : ranked) {
            if (selectedIds.size() >= command.count()) {
                break;
            }
            if (isValidMcq(optionsByQuestion.get(questionId)) && selected.add(questionId)) {
                selectedIds.add(questionId);
            }
        }
        if (selectedIds.isEmpty()) {
            throw new ConflictException(
                    "Chưa có đáp án trắc nghiệm cho module này. Cần chạy sinh đáp án trước khi làm quiz.");
        }

        Map<UUID, Question> questionsById = new LinkedHashMap<>();
        for (Question question : questionRepository.findByIds(selectedIds)) {
            questionsById.put(question.getId(), question);
        }

        selectedIds.removeIf(id -> !questionsById.containsKey(id));
        if (selectedIds.isEmpty()) throw new ConflictException("Nội dung vừa thay đổi, thử lại");
        QuizSession session = QuizSession.start(userId, command.strategy(), selectedIds, Instant.now(clock));
        QuizSession saved = sessionRepository.save(session);

        List<QuizQuestion> questions = new ArrayList<>(selectedIds.size());
        for (UUID questionId : selectedIds) {
            Question question = questionsById.get(questionId);
            if (question == null) {
                // Câu bị xoá giữa lúc rank và lúc đọc: bỏ khỏi phiên thay vì trả DTO thiếu nội dung.
                continue;
            }
            questions.add(new QuizQuestion(question, optionsByQuestion.get(questionId)));
        }
        if (questions.isEmpty()) {
            throw new ConflictException("Câu hỏi của module vừa bị thay đổi, thử lại");
        }
        return new GeneratedQuiz(saved, List.copyOf(questions));
    }

    private static boolean isValidMcq(List<QuestionOption> options) {
        if (options == null || options.size() < MIN_OPTIONS) {
            return false;
        }
        return options.stream().filter(QuestionOption::isCorrect).count() == 1;
    }
}
