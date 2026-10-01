package com.knowledgegym.learning.application.strategy;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.service.RandomOrder;
import com.knowledgegym.shared.domain.model.Difficulty;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * `RANDOM` — trộn ngẫu nhiên toàn bộ câu của module. Dùng khi user muốn "kiểm tra tổng quát",
 * không nhắm vào điểm yếu nào.
 */
public final class RandomQuizStrategy implements QuizGenerationStrategy {

    private final QuizCandidatePool pool;

    public RandomQuizStrategy(QuizCandidatePool pool) {
        this.pool = pool;
    }

    @Override
    public QuizStrategy strategy() {
        return QuizStrategy.RANDOM;
    }

    @Override
    public List<UUID> rank(UUID userId, UUID moduleId, Difficulty difficulty, RandomGenerator random) {
        List<Question> questions = pool.questionsOf(moduleId, difficulty);
        return RandomOrder.shuffled(questions, random).stream().map(Question::getId).toList();
    }
}
