package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.learning.domain.port.QuizSessionRepository;
import com.knowledgegym.learning.domain.model.QuizSession;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QueryQuizUseCase {
    private final QuizSessionRepository sessions;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;

    public QueryQuizUseCase(QuizSessionRepository sessions, QuestionRepository questions,
                            QuestionOptionRepository options) {
        this.sessions = sessions;
        this.questions = questions;
        this.options = options;
    }

    @Transactional(readOnly = true)
    public GenerateQuizUseCase.GeneratedQuiz get(UUID user, UUID id) {
        var session = sessions.findByIdAndUserId(id, user)
                .orElseThrow(() -> new NotFoundException("Phiên quiz không tồn tại"));
        var byId = questions.findByIds(session.getQuestionIds()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        var byQuestion = options.findByQuestionIds(session.getQuestionIds());
        var ordered = session.getQuestionIds().stream().filter(byId::containsKey)
                .map(questionId -> new GenerateQuizUseCase.QuizQuestion(byId.get(questionId),
                        byQuestion.getOrDefault(questionId, List.of()))).toList();
        return new GenerateQuizUseCase.GeneratedQuiz(session, ordered);
    }

    public record History(List<QuizSession> items, int page, int size, long totalElements, int totalPages) {}

    @Transactional(readOnly = true)
    public History history(UUID user, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page >= 1, size 1..100");
        }
        long total = sessions.countByUserId(user);
        return new History(sessions.findByUserId(user, page, size), page, size, total,
                (int) ((total + size - 1) / size));
    }
}
