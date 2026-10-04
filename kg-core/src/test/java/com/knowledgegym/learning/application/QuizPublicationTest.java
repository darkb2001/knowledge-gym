package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.learning.application.strategy.QuizGenerationStrategy;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.port.QuizSessionRepository;
import com.knowledgegym.shared.application.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizPublicationTest {
    @Test
    void questionWithdrawnAfterRankingCannotCreateANewQuiz() {
        var questions = new LearningTestSupport.InMemoryQuestionRepository();
        var question = LearningTestSupport.question(UUID.randomUUID(), "Ranked before withdrawal", 1);
        questions.seed(question);
        var strategy = mock(QuizGenerationStrategy.class);
        when(strategy.rank(any(), any(), any(), any())).thenReturn(List.of(question.getId()));
        var options = mock(QuestionOptionRepository.class);
        when(options.findByQuestionIds(any())).thenReturn(Map.of(question.getId(), List.of(
                new QuestionOption("Right", true, 1), new QuestionOption("Wrong", false, 2))));
        var sessions = mock(QuizSessionRepository.class);
        var useCase = new GenerateQuizUseCase(questions, options, sessions,
                Map.of(QuizStrategy.RANDOM, strategy), new Random(1), Clock.systemUTC());
        for (var status : Question.ContentStatus.values()) {
            if (status == Question.ContentStatus.PUBLISHED) continue;
            question.setContentStatus(status);
            assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), new GenerateQuizUseCase.GenerateCommand(
                    question.getModuleId(), 1, QuizStrategy.RANDOM, null)))
                    .isInstanceOf(ConflictException.class);
        }
        verify(sessions, never()).save(any());
    }
}
