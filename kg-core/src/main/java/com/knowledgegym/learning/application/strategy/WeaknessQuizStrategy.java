package com.knowledgegym.learning.application.strategy;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.port.StudyAttemptRepository;
import com.knowledgegym.learning.domain.service.RandomOrder;
import com.knowledgegym.shared.domain.model.Difficulty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * `WEAKNESS` — đặt câu user từng trả lời sai lên đầu, phần còn lại của module xếp sau.
 *
 * <p>Nguồn tín hiệu là `study_attempts.is_correct = FALSE` (P2 trong plan). Không lọc cứng "chỉ câu
 * từng sai": user mới chưa có lịch sử sẽ nhận phiên rỗng, mà "chưa sai bao giờ" không có nghĩa "không
 * cần luyện". Vì vậy câu từng sai lên trước, phần còn lại lấp đầy phiên.
 *
 * <p>Loại câu không còn tồn tại (đã bị re-import xoá) — `study_attempts` được dọn theo nhưng
 * `question_id` từ nguồn khác vẫn phải đối chiếu với pool trước khi trả về.
 */
public final class WeaknessQuizStrategy implements QuizGenerationStrategy {

    private final QuizCandidatePool pool;
    private final StudyAttemptRepository attemptRepository;

    public WeaknessQuizStrategy(QuizCandidatePool pool, StudyAttemptRepository attemptRepository) {
        this.pool = pool;
        this.attemptRepository = attemptRepository;
    }

    @Override
    public QuizStrategy strategy() {
        return QuizStrategy.WEAKNESS;
    }

    @Override
    public List<UUID> rank(UUID userId, UUID moduleId, Difficulty difficulty, RandomGenerator random) {
        List<Question> questions = pool.questionsOf(moduleId, difficulty);
        Set<UUID> inModule = new LinkedHashSet<>(questions.stream().map(Question::getId).toList());

        // `findWrongQuestionIdsByUser` đã xếp theo số lần sai giảm dần → giữ nguyên thứ tự này,
        // chỉ lọc câu không thuộc module/filter hiện tại.
        List<UUID> wrongFirst = new ArrayList<>();
        for (UUID id : attemptRepository.findWrongQuestionIdsByUser(userId)) {
            if (inModule.contains(id)) {
                wrongFirst.add(id);
            }
        }
        Set<UUID> ranked = new LinkedHashSet<>(wrongFirst);

        List<UUID> rest = RandomOrder.shuffled(
                questions.stream().map(Question::getId).filter(id -> !ranked.contains(id)).toList(),
                random);
        ranked.addAll(rest);
        return List.copyOf(ranked);
    }
}
