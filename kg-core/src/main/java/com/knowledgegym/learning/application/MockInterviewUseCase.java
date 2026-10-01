package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.port.*;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.service.PlainText;
import com.knowledgegym.learning.application.strategy.QuizCandidatePool;
import com.knowledgegym.learning.domain.model.InterviewAnswer;
import com.knowledgegym.learning.domain.model.InterviewSession;
import com.knowledgegym.learning.domain.port.InterviewSessionRepository;
import com.knowledgegym.learning.domain.service.KeywordGrader;
import com.knowledgegym.learning.domain.service.RandomOrder;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
                "ACTIVE", null, Instant.now(clock), null, selected.stream().map(Question::getId).toList());
        return new Started(sessions.save(session), selected.stream()
                .map(q -> new QuestionPrompt(q.getId(), q.getTitle())).toList());
    }

    private InterviewSession active(UUID user, UUID id) {
        var session = sessions.findByIdAndUserIdForUpdate(id, user)
                .orElseThrow(() -> new NotFoundException("Phiên phỏng vấn không tồn tại"));
        if (!"ACTIVE".equals(session.status())) throw new ConflictException("Phiên phỏng vấn đã kết thúc");
        return session;
    }

    @Transactional
    public InterviewAnswer answer(UUID user, UUID id, UUID questionId, String answer) {
        var session = active(user, id);
        if (questionId == null || answer == null || answer.isBlank() || answer.length() > 20000) {
            throw new IllegalArgumentException("Câu trả lời bắt buộc, tối đa 20000 ký tự");
        }
        if (!session.questionIds().contains(questionId)) {
            throw new IllegalArgumentException("Câu hỏi không thuộc phiên phỏng vấn");
        }
        var question = questions.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Câu hỏi đã bị xoá"));
        String sample = PlainText.of(question.getAnswerHtml());
        var grade = KeywordGrader.grade(sample, answer);
        var result = new InterviewAnswer(id, questionId, answer, grade.score(), grade.feedback(),
                sample, Instant.now(clock));
        sessions.upsertAnswer(result);
        return result;
    }

    @Transactional
    public InterviewSession finish(UUID user, UUID id) {
        var session = active(user, id);
        // Placeholders have NULL score and are excluded by the repository.
        var answers = sessions.findSubmittedAnswers(id);
        BigDecimal score = answers.isEmpty() ? BigDecimal.ZERO : answers.stream()
                .map(InterviewAnswer::keywordScore).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(answers.size()), 2, RoundingMode.HALF_UP);
        Instant now = Instant.now(clock);
        sessions.finish(id, score, now);
        return new InterviewSession(session.id(), session.userId(), session.topicId(),
                session.questionCount(), session.mode(), "FINISHED", score, session.startedAt(),
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
