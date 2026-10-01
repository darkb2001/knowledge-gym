package com.knowledgegym.learning.application.strategy;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.learning.domain.model.QuizStrategy;
import com.knowledgegym.learning.domain.model.SRSCard;
import com.knowledgegym.learning.domain.port.SRSCardRepository;
import com.knowledgegym.shared.domain.model.Difficulty;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * `SPACED` — chỉ hỏi những câu đến hạn ôn theo SM-2 trong module.
 *
 * <p><b>Không viết lại logic "đến hạn":</b> thẻ đọc qua {@link SRSCardRepository#findDue} và tập đến
 * hạn lọc trên pool của module — cùng predicate `next_review <= today` mà `QueryDueUseCase` dùng, nên
 * quiz và phiên flashcard không bao giờ lệch nhau về "hôm nay học gì".
 *
 * <p>Thứ tự trong nhóm đến hạn giữ theo `nextReview` tăng dần (quá hạn lâu nhất trước), khớp cách
 * `QueryDueUseCase` sắp — người học thấy cùng một hàng đợi ở cả hai chế độ.
 */
public final class SpacedQuizStrategy implements QuizGenerationStrategy {

    private final QuizCandidatePool pool;
    private final SRSCardRepository cardRepository;
    private final Clock clock;

    public SpacedQuizStrategy(QuizCandidatePool pool, SRSCardRepository cardRepository, Clock clock) {
        this.pool = pool;
        this.cardRepository = cardRepository;
        this.clock = clock;
    }

    @Override
    public QuizStrategy strategy() {
        return QuizStrategy.SPACED;
    }

    @Override
    public List<UUID> rank(UUID userId, UUID moduleId, Difficulty difficulty, RandomGenerator random) {
        List<Question> questions = pool.questionsOf(moduleId, difficulty);
        Set<UUID> inModule = new LinkedHashSet<>(questions.stream().map(Question::getId).toList());

        List<UUID> dueFirst = new ArrayList<>();
        for (SRSCard card : cardRepository.findDue(userId, LocalDate.now(clock))) {
            if (inModule.contains(card.getQuestionId())) {
                dueFirst.add(card.getQuestionId());
            }
        }

        return List.copyOf(new LinkedHashSet<>(dueFirst));
    }
}
