package com.knowledgegym.presentation.rest.quiz.dto;

import com.knowledgegym.learning.application.GenerateQuizUseCase;
import com.knowledgegym.learning.domain.model.QuizSession;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuizResponses {
    private QuizResponses() {}
    public record Option(UUID id, String content) {}
    public record Question(UUID questionId, String title, List<Option> options) {}
    public record Summary(UUID id, QuizStrategy strategy, Integer score, int total,
                          Instant startedAt, Instant finishedAt) {
        public static Summary of(QuizSession s) {
            return new Summary(s.getId(), s.getStrategy(), s.getScore(), s.getTotal(),
                    s.getStartedAt(), s.getFinishedAt());
        }
    }
    public record Session(UUID id, QuizStrategy strategy, Integer score, int total,
                          Instant startedAt, Instant finishedAt, List<Question> questions, int timeLimit) {
        public static Session of(GenerateQuizUseCase.GeneratedQuiz quiz) {
            var s = quiz.session();
            var questions = quiz.questions().stream().map(q -> new Question(
                    q.question().getId(), q.question().getTitle(), q.options().stream()
                    .map(o -> new Option(o.getId(), o.getContent())).toList())).toList();
            return new Session(s.getId(), s.getStrategy(), s.getScore(), s.getTotal(),
                    s.getStartedAt(), s.getFinishedAt(), questions, quiz.timeLimitSeconds());
        }
    }
}
