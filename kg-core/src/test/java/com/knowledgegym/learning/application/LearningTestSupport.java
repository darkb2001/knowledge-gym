package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.learning.domain.model.SRSCard;
import com.knowledgegym.learning.domain.model.SrsDeck;
import com.knowledgegym.learning.domain.model.StudyAttempt;
import com.knowledgegym.learning.domain.port.SRSCardRepository;
import com.knowledgegym.learning.domain.port.SrsDeckRepository;
import com.knowledgegym.learning.domain.port.StudyAttemptRepository;
import com.knowledgegym.shared.domain.model.Difficulty;
import com.knowledgegym.shared.domain.model.PageResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fakes cho use case m5 — không cần Spring/DB nên test chạy trong vài ms.
 *
 * <p>Cố ý bám sát ràng buộc thật của DB thay vì "dễ dùng": `insertIgnoringDuplicates` bỏ qua thẻ
 * đã tồn tại (như `ON CONFLICT DO NOTHING`) và chỉ trả id thẻ mới (như `RETURNING id`). Nếu fake
 * đơn giản hoá chỗ này, test "enroll 2 lần không tạo trùng" sẽ xanh kể cả khi SQL sai.
 */
final class LearningTestSupport {

    private LearningTestSupport() {
    }

    static Question question(UUID moduleId, String title, int sortOrder) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setModuleId(moduleId);
        question.setTitle(title);
        question.setAnswerHtml("<p>" + title + "</p>");
        question.setDifficulty(Difficulty.MID);
        question.setSortOrder(sortOrder);
        return question;
    }

    static final class InMemoryQuestionRepository implements QuestionRepository {

        private final Map<UUID, Question> store = new LinkedHashMap<>();

        void seed(Question question) {
            store.put(question.getId(), question);
        }

        @Override
        public Optional<Question> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Question> findByIds(Collection<UUID> ids) {
            return ids.stream().map(store::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public Optional<Question> findByModuleIdAndSortOrder(UUID moduleId, int sortOrder) {
            return store.values().stream()
                    .filter(q -> moduleId.equals(q.getModuleId()) && q.getSortOrder() == sortOrder)
                    .findFirst();
        }

        /** Phân trang thật để test enroll module > 1 trang không bị cắt im lặng. */
        @Override
        public PageResult<Question> search(QuestionQuery query) {
            List<Question> matched = store.values().stream()
                    .filter(q -> query.moduleId() == null || query.moduleId().equals(q.getModuleId()))
                    .filter(q -> query.difficulty() == null || query.difficulty() == q.getDifficulty())
                    .sorted(Comparator.comparingInt(Question::getSortOrder))
                    .toList();
            int from = (int) Math.min(query.offset(), matched.size());
            int to = (int) Math.min(from + query.size(), matched.size());
            return new PageResult<>(matched.subList(from, to), query.page(), query.size(), matched.size());
        }

        @Override
        public int saveOrUpdateByNaturalKey(List<Question> questions) {
            questions.forEach(this::seed);
            return questions.size();
        }

        @Override
        public int deleteAbsentSortOrders(UUID moduleId, Collection<Integer> keepSortOrders) {
            return 0;
        }

        @Override
        public Question save(Question question) {
            seed(question);
            return question;
        }

        @Override
        public void deleteById(UUID id) {
            store.remove(id);
        }

        @Override
        public int nextSortOrder(UUID moduleId) {
            return store.size() + 1;
        }
    }

    static final class InMemoryModuleRepository implements ModuleRepository {

        private final Map<UUID, ModuleRef> store = new LinkedHashMap<>();

        void seed(ModuleRef module) {
            store.put(module.getId(), module);
        }

        @Override
        public Optional<ModuleRef> findBySlug(String slug) {
            return store.values().stream().filter(m -> m.getSlug().equals(slug)).findFirst();
        }

        @Override
        public Optional<ModuleRef> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public ModuleRef saveOrUpdateBySlug(ModuleRef module) {
            seed(module);
            return module;
        }

        @Override
        public List<ModuleRef> findByTopicIdOrdered(UUID topicId) {
            return List.of();
        }

        @Override
        public List<ModuleRef> findAllOrdered() {
            return new ArrayList<>(store.values());
        }

        @Override
        public long countQuestions(UUID moduleId) {
            return 0;
        }

        @Override
        public Map<UUID, Long> countQuestionsByModule() {
            return Map.of();
        }
    }

    static final class InMemorySRSCardRepository implements SRSCardRepository {

        final Map<UUID, SRSCard> store = new LinkedHashMap<>();

        @Override
        public Optional<SRSCard> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        /** Fake không mô phỏng khoá — chỉ lọc theo chủ sở hữu như bản thật. */
        @Override
        public Optional<SRSCard> findByIdForUpdate(UUID id, UUID userId) {
            return Optional.ofNullable(store.get(id))
                    .filter(card -> card.getUserId().equals(userId));
        }

        @Override
        public List<SRSCard> findDue(UUID userId, LocalDate today) {
            return store.values().stream()
                    .filter(card -> card.getUserId().equals(userId))
                    .filter(card -> card.isDueOn(today))
                    .toList();
        }

        @Override
        public SRSCard save(SRSCard card) {
            store.put(card.getId(), card);
            return card;
        }

        @Override
        public long countByUserId(UUID userId) {
            return store.values().stream().filter(c -> c.getUserId().equals(userId)).count();
        }

        /** Mô phỏng `ON CONFLICT (user_id, question_id) DO NOTHING` + `RETURNING id`. */
        @Override
        public List<UUID> insertIgnoringDuplicates(UUID userId, UUID deckId, Collection<UUID> questionIds,
                                                   LocalDate nextReview) {
            List<UUID> created = new ArrayList<>();
            for (UUID questionId : questionIds) {
                boolean exists = store.values().stream().anyMatch(card ->
                        card.getUserId().equals(userId) && card.getQuestionId().equals(questionId));
                if (exists) {
                    continue;
                }
                SRSCard card = SRSCard.newCard(userId, questionId, deckId, null, nextReview);
                store.put(card.getId(), card);
                created.add(card.getId());
            }
            return created;
        }
    }

    static final class InMemorySrsDeckRepository implements SrsDeckRepository {

        final Map<UUID, SrsDeck> store = new LinkedHashMap<>();

        @Override
        public SrsDeck save(SrsDeck deck) {
            deck.setId(deck.getId() != null ? deck.getId() : UUID.randomUUID());
            store.put(deck.getId(), deck);
            return deck;
        }

        @Override
        public Optional<SrsDeck> findByUserIdAndModuleId(UUID userId, UUID moduleId) {
            return store.values().stream()
                    .filter(deck -> deck.getUserId().equals(userId) && moduleId.equals(deck.getModuleId()))
                    .findFirst();
        }

        @Override
        public Optional<SrsDeck> findByIdAndUserId(UUID id, UUID userId) {
            return store.values().stream()
                    .filter(deck -> deck.getId().equals(id) && deck.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<SrsDeck> findByUserId(UUID userId) {
            return store.values().stream().filter(d -> d.getUserId().equals(userId)).toList();
        }
    }

    static final class InMemoryStudyAttemptRepository implements StudyAttemptRepository {
        public List<UUID> findWrongQuestionIdsByUser(UUID userId) {
            return store.stream().filter(a -> a.getUserId().equals(userId) && !a.isCorrect()).map(StudyAttempt::getQuestionId).distinct().toList();
        }

        final List<StudyAttempt> store = new ArrayList<>();

        @Override
        public StudyAttempt save(StudyAttempt attempt) {
            attempt.setId(attempt.getId() != null ? attempt.getId() : UUID.randomUUID());
            store.add(attempt);
            return attempt;
        }
    }
}
