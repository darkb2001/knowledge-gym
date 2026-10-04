package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.port.*;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.learning.application.strategy.QuizCandidatePool;
import com.knowledgegym.learning.domain.model.InterviewAnswer;
import com.knowledgegym.learning.domain.model.InterviewSession;
import com.knowledgegym.learning.domain.port.InterviewSessionRepository;
import com.knowledgegym.learning.domain.service.RandomOrder;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/** TEXT interview lifecycle; session row locks serialize edits with finish. */
public class MockInterviewUseCase {
    private final InterviewSessionRepository sessions;
    private final QuestionRepository questions;
    private final ModuleRepository modules;
    private final TopicRepository topics;
    private final Clock clock;

    public MockInterviewUseCase(InterviewSessionRepository sessions, QuestionRepository questions,
                                ModuleRepository modules, TopicRepository topics, Clock clock) {
        this.sessions = sessions;
        this.questions = questions;
        this.modules = modules;
        this.topics = topics;
        this.clock = clock;
    }

    public record Started(InterviewSession session, List<QuestionPrompt> questions) {}
    public record QuestionPrompt(UUID questionId, String title) {}

    /** 1 câu trong payload submit toàn cục; `answer` rỗng = user bỏ trống câu đó. */
    public record AnswerInput(UUID questionId, String answer) {}

    public record ResultItem(UUID questionId, String title, String userAnswer, String answerHtml,
                             Instant answeredAt) {}

    public record Result(InterviewSession session, List<ResultItem> items, int answeredCount) {}

    @Transactional
    public Started start(UUID user, UUID topic, int count, String mode) {
        if (topic == null || count < 1 || count > 20 || !"TEXT".equals(mode)) {
            throw new IllegalArgumentException("topicId bắt buộc, questionCount 1..20, chỉ hỗ trợ TEXT");
        }
        if (topics.findAllOrdered().stream().noneMatch(t -> t.getId().equals(topic))) {
            throw new NotFoundException("Topic không tồn tại");
        }
        var pool = new QuizCandidatePool(questions, modules);
        List<Question> candidates = new ArrayList<>();
        for (var module : modules.findByTopicIdOrdered(topic)) {
            candidates.addAll(pool.questionsOf(module.getId(), null));
        }
        var selected = RandomOrder.shuffled(candidates, ThreadLocalRandom.current()).stream()
                .limit(count).toList();
        if (selected.isEmpty()) throw new ConflictException("Topic chưa có câu hỏi");
        var session = new InterviewSession(UUID.randomUUID(), user, topic, selected.size(), mode,
                "ACTIVE", Instant.now(clock), null, selected.stream().map(Question::getId).toList());
        return new Started(sessions.save(session), selected.stream()
                .map(q -> new QuestionPrompt(q.getId(), q.getTitle())).toList());
    }

    private InterviewSession active(UUID user, UUID id) {
        var session = sessions.findByIdAndUserIdForUpdate(id, user)
                .orElseThrow(() -> new NotFoundException("Phiên phỏng vấn không tồn tại"));
        if (!"ACTIVE".equals(session.status())) throw new ConflictException("Phiên phỏng vấn đã kết thúc");
        return session;
    }

    /**
     * Nút "kết thúc phỏng vấn" = submit toàn cục: nhận mọi câu trả lời trong 1 request rồi đóng
     * phiên. Câu bỏ trống vẫn được chấp nhận — user luôn được đưa sang trang kết quả.
     */
    @Transactional
    public Result submit(UUID user, UUID id, List<AnswerInput> answers) {
        var session = active(user, id);
        if (answers != null) {
            if (answers.size() > 100) {
                throw new IllegalArgumentException("Tối đa 100 câu trả lời mỗi lần nộp");
            }
            for (AnswerInput input : answers) {
                if (input == null || input.questionId() == null) {
                    continue;
                }
                String text = input.answer() == null ? "" : input.answer().trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (text.length() > 20000) {
                    throw new IllegalArgumentException("Câu trả lời tối đa 20000 ký tự");
                }
                if (!session.questionIds().contains(input.questionId())) {
                    throw new IllegalArgumentException("Câu hỏi không thuộc phiên phỏng vấn");
                }
                var question = questions.findById(input.questionId())
                        .orElseThrow(() -> new NotFoundException("Câu hỏi đã bị xoá"));
                sessions.upsertAnswer(new InterviewAnswer(id, input.questionId(), text,
                        question.getAnswerHtml(), Instant.now(clock)));
            }
        }
        sessions.finish(id, Instant.now(clock));
        return result(user, id);
    }

    /** Trang kết quả: đủ MỌI câu của phiên kèm đáp án mẫu, kể cả câu user chưa trả lời. */
    @Transactional(readOnly = true)
    public Result result(UUID user, UUID id) {
        var session = sessions.findByIdAndUserId(id, user)
                .orElseThrow(() -> new NotFoundException("Phiên phỏng vấn không tồn tại"));
        var saved = sessions.answersOf(id);
        List<ResultItem> items = session.questionIds().stream().map(questionId -> {
            var answer = saved.get(questionId);
            var question = questions.findById(questionId).orElse(null);
            String stored = answer == null ? null : answer.answerHtml();
            return new ResultItem(questionId,
                    question == null ? "Câu hỏi đã bị xoá" : question.getTitle(),
                    answer == null ? null : answer.userAnswer(),
                    stored != null ? stored : (question == null ? null : question.getAnswerHtml()),
                    answer == null ? null : answer.attemptedAt());
        }).toList();
        int answered = (int) items.stream().filter(item -> item.userAnswer() != null).count();
        return new Result(session, items, answered);
    }

    @Transactional
    public InterviewSession finish(UUID user, UUID id) {
        var session = active(user, id);
        Instant now = Instant.now(clock);
        sessions.finish(id, now);
        return new InterviewSession(session.id(), session.userId(), session.topicId(),
                session.questionCount(), session.mode(), "FINISHED", session.startedAt(),
                now, session.questionIds());
    }

    public record History(List<InterviewSession> items, int page, int size, long totalElements, int totalPages) {}

    @Transactional(readOnly = true)
    public History history(UUID user, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("page >= 1, size 1..100");
        long total = sessions.countByUserId(user);
        return new History(sessions.findByUserId(user, page, size), page, size, total,
                (int) ((total + size - 1) / size));
    }
}
