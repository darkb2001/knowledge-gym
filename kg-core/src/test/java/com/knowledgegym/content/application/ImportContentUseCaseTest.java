package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ContentCatalog;
import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.ParsedQuestion;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.model.Topic;
import com.knowledgegym.content.domain.port.ContentSource;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.content.domain.port.TopicRepository;
import com.knowledgegym.shared.domain.model.Difficulty;
import com.knowledgegym.shared.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-import coi docs là source of truth: card biến mất khỏi HTML → hàng `(module, sortOrder)`
 * phải bị xoá, không được để stale.
 */
class ImportContentUseCaseTest {

    private InMemoryTopicRepository topics;
    private InMemoryModuleRepository modules;
    private InMemoryQuestionRepository questions;
    private AtomicReference<ContentCatalog> catalog;
    private ImportContentUseCase useCase;

    @BeforeEach
    void setUp() {
        topics = new InMemoryTopicRepository();
        modules = new InMemoryModuleRepository();
        questions = new InMemoryQuestionRepository();
        catalog = new AtomicReference<>();
        ContentSource source = path -> catalog.get();
        useCase = new ImportContentUseCase(source, topics, modules, questions);
    }

    @Test
    void reimportDeletesQuestionsRemovedFromDocs() {
        catalog.set(catalogWithQuestions(
                question("m1", "Q1", 1),
                question("m1", "Q2", 2),
                question("m1", "Q3", 3)));

        ImportContentUseCase.ImportResult first = useCase.execute("/docs");
        assertThat(first.questions()).isEqualTo(3);
        assertThat(first.deleted()).isEqualTo(0);
        assertThat(questions.store).hasSize(3);

        // Docs shrink: bỏ Q3 (sortOrder=3)
        catalog.set(catalogWithQuestions(
                question("m1", "Q1", 1),
                question("m1", "Q2-updated", 2)));

        ImportContentUseCase.ImportResult second = useCase.execute("/docs");
        assertThat(second.questions()).isEqualTo(2);
        assertThat(second.deleted()).isEqualTo(1);
        assertThat(questions.store.values())
                .extracting(Question::getTitle)
                .containsExactlyInAnyOrder("Q1", "Q2-updated");
        assertThat(questions.store.values())
                .extracting(Question::getSortOrder)
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void reimportDeletesAllQuestionsWhenModuleHasNoCards() {
        catalog.set(catalogWithQuestions(question("m1", "Only", 1)));
        useCase.execute("/docs");
        assertThat(questions.store).hasSize(1);

        catalog.set(catalogWithQuestions()); // module vẫn trong catalog, 0 card
        ImportContentUseCase.ImportResult result = useCase.execute("/docs");

        assertThat(result.questions()).isEqualTo(0);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(questions.store).isEmpty();
    }

    private static ContentCatalog catalogWithQuestions(ParsedQuestion... parsed) {
        Topic topic = new Topic("Topic 1", "t1", 1);
        ModuleRef module = new ModuleRef("Module 1", "m1", 1);
        module.setTopicSlug("t1");
        return new ContentCatalog(List.of(topic), List.of(module), List.of(parsed));
    }

    private static ParsedQuestion question(String moduleSlug, String title, int sortOrder) {
        return new ParsedQuestion(moduleSlug, title, "<p>" + title + "</p>", List.of("tag"),
                List.of(title.toLowerCase()), sortOrder, Difficulty.MID);
    }

    private static final class InMemoryTopicRepository implements TopicRepository {
        private final Map<String, Topic> bySlug = new HashMap<>();

        @Override
        public Optional<Topic> findBySlug(String slug) {
            return Optional.ofNullable(bySlug.get(slug));
        }

        @Override
        public List<Topic> findAllOrdered() {
            return bySlug.values().stream()
                    .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                    .toList();
        }

        @Override
        public Topic saveOrUpdateBySlug(Topic topic) {
            Topic existing = bySlug.get(topic.getSlug());
            if (existing != null) {
                existing.setName(topic.getName());
                existing.setDisplayOrder(topic.getDisplayOrder());
                return existing;
            }
            if (topic.getId() == null) {
                topic.setId(UUID.randomUUID());
            }
            bySlug.put(topic.getSlug(), topic);
            return topic;
        }
    }

    private static final class InMemoryModuleRepository implements ModuleRepository {
        private final Map<String, ModuleRef> bySlug = new HashMap<>();

        @Override
        public Optional<ModuleRef> findBySlug(String slug) {
            return Optional.ofNullable(bySlug.get(slug));
        }

        @Override
        public Optional<ModuleRef> findById(UUID id) {
            return bySlug.values().stream().filter(m -> id.equals(m.getId())).findFirst();
        }

        @Override
        public List<ModuleRef> findAllOrdered() {
            return new ArrayList<>(bySlug.values());
        }

        @Override
        public List<ModuleRef> findByTopicIdOrdered(UUID topicId) {
            return bySlug.values().stream()
                    .filter(m -> topicId.equals(m.getTopicId()))
                    .toList();
        }

        @Override
        public ModuleRef saveOrUpdateBySlug(ModuleRef module) {
            ModuleRef existing = bySlug.get(module.getSlug());
            if (existing != null) {
                existing.setName(module.getName());
                existing.setTopicId(module.getTopicId());
                existing.setTopicSlug(module.getTopicSlug());
                existing.setDisplayOrder(module.getDisplayOrder());
                return existing;
            }
            if (module.getId() == null) {
                module.setId(UUID.randomUUID());
            }
            bySlug.put(module.getSlug(), module);
            return module;
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

    private static final class InMemoryQuestionRepository implements QuestionRepository {
        final Map<UUID, Question> store = new HashMap<>();

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

        @Override
        public PageResult<Question> search(QuestionQuery query) {
            return new PageResult<>(List.copyOf(store.values()), 1, 10, store.size());
        }

        @Override
        public int saveOrUpdateByNaturalKey(List<Question> incoming) {
            for (Question question : incoming) {
                Optional<Question> existing = findByModuleIdAndSortOrder(
                        question.getModuleId(), question.getSortOrder());
                if (existing.isPresent()) {
                    Question row = existing.get();
                    row.setTitle(question.getTitle());
                    row.setAnswerHtml(question.getAnswerHtml());
                    row.setTags(question.getTags());
                    row.setSearchKeywords(question.getSearchKeywords());
                } else {
                    if (question.getId() == null) {
                        question.setId(UUID.randomUUID());
                    }
                    store.put(question.getId(), question);
                }
            }
            return incoming.size();
        }

        @Override
        public int deleteAbsentSortOrders(UUID moduleId, Collection<Integer> keepSortOrders) {
            List<UUID> toRemove = store.values().stream()
                    .filter(q -> moduleId.equals(q.getModuleId()))
                    .filter(q -> keepSortOrders == null || !keepSortOrders.contains(q.getSortOrder()))
                    .map(Question::getId)
                    .collect(Collectors.toList());
            toRemove.forEach(store::remove);
            return toRemove.size();
        }

        @Override
        public Question save(Question question) {
            if (question.getId() == null) {
                question.setId(UUID.randomUUID());
            }
            store.put(question.getId(), question);
            return question;
        }

        @Override
        public void deleteById(UUID id) {
            store.remove(id);
        }

        @Override
        public int nextSortOrder(UUID moduleId) {
            return store.values().stream()
                    .filter(q -> moduleId.equals(q.getModuleId()))
                    .mapToInt(Question::getSortOrder)
                    .max()
                    .orElse(0) + 1;
        }
    }
}
