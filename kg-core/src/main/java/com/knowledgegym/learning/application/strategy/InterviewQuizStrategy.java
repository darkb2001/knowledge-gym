package com.knowledgegym.learning.application.strategy;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.service.RandomOrder;
import com.knowledgegym.shared.domain.model.Difficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * `INTERVIEW` — mô phỏng buổi phỏng vấn: câu MID/SENIOR lên trước, JUNIOR lấp sau, trộn trong từng
 * nhóm để độ khó không đi theo thứ tự đoán được.
 *
 * <p>Ý niệm "trộn độ khó" cố ý không phải "sắp SENIOR → MID → JUNIOR": đó là một bài kiểm tra có
 * đường cong, còn phỏng vấn thật nhảy độ khó. Trộn trong nhóm giữ được ưu tiên mà không lộ trật tự.
 *
 * <p>Filter `difficulty` của client được tôn trọng trước: nếu user chọn JUNIOR thì không có gì để ưu
 * tiên nữa và kết quả là trộn thuần.
 */
public final class InterviewQuizStrategy implements QuizGenerationStrategy {

    private final QuizCandidatePool pool;

    public InterviewQuizStrategy(QuizCandidatePool pool) {
        this.pool = pool;
    }

    @Override
    public QuizStrategy strategy() {
        return QuizStrategy.INTERVIEW;
    }

    @Override
    public List<UUID> rank(UUID userId, UUID moduleId, Difficulty difficulty, RandomGenerator random) {
        List<Question> questions = pool.questionsOf(moduleId, difficulty);
        List<UUID> senior = new ArrayList<>();
        List<UUID> junior = new ArrayList<>();
        for (Question question : questions) {
            if (question.getDifficulty() == Difficulty.JUNIOR) {
                junior.add(question.getId());
            } else {
                // difficulty null (dữ liệu cũ) xếp cùng MID/SENIOR: coi như "không phải câu dễ".
                senior.add(question.getId());
            }
        }
        List<UUID> ranked = new ArrayList<>(RandomOrder.shuffled(senior, random));
        ranked.addAll(RandomOrder.shuffled(junior, random));
        return List.copyOf(ranked);
    }
}
