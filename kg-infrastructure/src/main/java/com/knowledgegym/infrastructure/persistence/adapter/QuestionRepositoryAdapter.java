package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.content.application.SearchText;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.infrastructure.persistence.dao.QuestionDependentsDao;
import com.knowledgegym.infrastructure.persistence.dao.QuestionSearchDao;
import com.knowledgegym.infrastructure.persistence.entity.QuestionJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataQuestionRepository;
import com.knowledgegym.shared.domain.model.Difficulty;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class QuestionRepositoryAdapter implements QuestionRepository {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final SpringDataQuestionRepository springData;
    private final QuestionSearchDao searchDao;
    private final QuestionDependentsDao dependentsDao;

    public QuestionRepositoryAdapter(SpringDataQuestionRepository springData, QuestionSearchDao searchDao,
                                     QuestionDependentsDao dependentsDao) {
        this.springData = springData;
        this.searchDao = searchDao;
        this.dependentsDao = dependentsDao;
    }

    @Override
    public Optional<Question> findById(UUID id) {
        return springData.findById(id).map(QuestionRepositoryAdapter::toDomain);
    }

    @Override
    public List<Question> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return springData.findAllById(ids).stream()
                .map(QuestionRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<Question> findByModuleIdAndSortOrder(UUID moduleId, int sortOrder) {
        return springData.findByModuleIdAndSortOrder(moduleId, sortOrder)
                .map(QuestionRepositoryAdapter::toDomain);
    }

    @Override
    public PageResult<Question> search(QuestionQuery query) {
        List<Question> items = searchDao.search(query).stream()
                .map(QuestionRepositoryAdapter::toDomain)
                .toList();
        long total = searchDao.count(query);
        return new PageResult<>(items, query.page(), query.size(), total);
    }

    @Override
    public PageResult<Question> searchAdmin(QuestionQuery query) {
        var items = searchDao.search(query, true).stream().map(QuestionRepositoryAdapter::toDomain).toList();
        return new PageResult<>(items, query.page(), query.size(), searchDao.count(query, true));
    }

    @Override
    @Transactional
    public int saveOrUpdateByNaturalKey(List<Question> questions) {
        if (questions.isEmpty()) {
            return 0;
        }
        int written = 0;
        for (Question question : questions) {
            int rows = springData.upsert(
                    question.getId() != null ? question.getId() : UUID.randomUUID(),
                    question.getModuleId(),
                    question.getTitle(),
                    question.getAnswerHtml(),
                    question.getDifficulty().name(),
                    toPgArrayLiteral(question.getTags()),
                    question.getSortOrder(),
                    String.join(" ", question.getSearchKeywords()));
            written += rows;
        }
        return written;
    }

    @Override
    @Transactional
    public int deleteAbsentSortOrders(UUID moduleId, Collection<Integer> keepSortOrders) {
        // Fail closed when removal would erase/detach learning or authored content history.
        dependentsDao.deleteForModule(moduleId, keepSortOrders);
        if (keepSortOrders == null || keepSortOrders.isEmpty()) {
            long before = springData.countByModuleId(moduleId);
            springData.deleteByModuleId(moduleId);
            return (int) before;
        }
        return springData.deleteByModuleIdAndSortOrderNotIn(moduleId, keepSortOrders);
    }

    @Override
    public Question save(Question question) {
        QuestionJpaEntity entity = question.getId() == null
                ? new QuestionJpaEntity()
                : springData.findById(question.getId()).orElseGet(QuestionJpaEntity::new);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            entity.setVersion(1);
        }
        entity.setModuleId(question.getModuleId());
        entity.setTitle(question.getTitle());
        entity.setAnswerHtml(question.getAnswerHtml());
        entity.setDifficulty(question.getDifficulty().name());
        entity.setContentStatus(question.getContentStatus().name());
        entity.setTags(question.getTags().toArray(String[]::new));
        entity.setSortOrder(question.getSortOrder());
        entity.setSearchableText(String.join(" ", question.getSearchKeywords()));
        entity.setUpdatedAt(Instant.now());
        return toDomain(springData.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        // Referenced content must be withdrawn through the audited status endpoint instead.
        dependentsDao.deleteForQuestion(id);
        springData.deleteById(id);
    }

    @Override
    public int nextSortOrder(UUID moduleId) {
        return springData.findMaxSortOrder(moduleId) + 1;
    }

    @Override @Transactional
    public Question updateContentStatus(UUID id, Question.ContentStatus status, UUID actor, String reason) {
        var entity = entityManager.find(QuestionJpaEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) throw new com.knowledgegym.shared.application.NotFoundException("Câu hỏi không tồn tại");
        String previous = entity.getContentStatus();
        entity.setContentStatus(status.name());
        entity.setUpdatedAt(Instant.now());
        entityManager.flush();
        entityManager.createNativeQuery("INSERT INTO audit_logs(user_id,action,entity_type,entity_id,details) VALUES(:actor,'QUESTION_STATUS','QUESTION',:id,jsonb_build_object('from',CAST(:previous AS text),'to',CAST(:status AS text),'reason',CAST(:reason AS text)))")
                .setParameter("actor",actor).setParameter("id",id).setParameter("previous",previous)
                .setParameter("status",status.name()).setParameter("reason",reason).executeUpdate();
        return toDomain(entity);
    }

    static Question toDomain(QuestionJpaEntity entity) {
        Question question = new Question();
        question.setId(entity.getId());
        question.setModuleId(entity.getModuleId());
        question.setTitle(entity.getTitle());
        question.setAnswerHtml(entity.getAnswerHtml());
        question.setDifficulty(parseDifficulty(entity.getDifficulty()));
        try { question.setContentStatus(Question.ContentStatus.valueOf(entity.getContentStatus())); } catch (Exception ignored) { }
        question.setTags(entity.getTags() == null ? List.of() : Arrays.asList(entity.getTags()));
        question.setSearchKeywords(tokenize(entity.getSearchableText()));
        question.setSortOrder(entity.getSortOrder());
        question.setCreatedAt(entity.getCreatedAt());
        question.setUpdatedAt(entity.getUpdatedAt());
        return question;
    }

    private static Difficulty parseDifficulty(String raw) {
        if (raw == null) {
            return Difficulty.MID;
        }
        try {
            return Difficulty.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Difficulty.MID;
        }
    }

    private static List<String> tokenize(String searchableText) {
        return SearchText.tokenList(searchableText);
    }

    /**
     * Mảng PostgreSQL dạng literal `{a,b}` cho native query — tag là slug `[a-z0-9-]` nên
     * không cần quote từng phần tử. Defense-in-depth: loại mọi ký tự ngoài whitelist.
     */
    static String toPgArrayLiteral(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return values.stream()
                .map(value -> value.replaceAll("[^A-Za-z0-9_-]", ""))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
