package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.AnswerHtmlSanitizer;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Admin CRUD invariants — chạy với fake repository nên không cần Spring/DB:
 * 1. `answerHtml` phải qua sanitizer (chặn stored XSS từ admin, không chỉ từ import).
 * 2. `searchKeywords` phải được tính lại khi title/answer/tags đổi, nếu không `searchable_text`
 *    giữ nội dung cũ và full-text search trả kết quả lệch dữ liệu.
 * 3. `sortOrder` trùng phải là 409, không được upsert ghi đè im lặng.
 */
class ManageQuestionsUseCaseTest {

    private static final UUID MODULE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private InMemoryQuestionRepository questions;
    private ManageQuestionsUseCase useCase;

    /** Sanitizer giả: đánh dấu rõ ràng để test phân biệt được "đã gọi". */
    private static final AnswerHtmlSanitizer SANITIZER =
            html -> html == null ? "" : html.replaceAll("(?is)<script.*?</script>", "").trim();

    @BeforeEach
    void setUp() {
        questions = new InMemoryQuestionRepository();
        useCase = new ManageQuestionsUseCase(questions, new InMemoryModuleRepository(), SANITIZER);
    }

    // ------------------------------------------------------------------ create

    @Test
    void createSanitizesAnswerHtmlBeforePersist() {
        Question created = useCase.create(new ManageQuestionsUseCase.CreateCommand(
                MODULE_ID, "Sao lưu dữ liệu", "<p>giữ lại</p><script>alert(1)</script>",
                Difficulty.SENIOR, List.of("backup"), null));

        assertThat(created.getAnswerHtml()).isEqualTo("<p>giữ lại</p>");
        assertThat(questions.findById(created.getId()).orElseThrow().getAnswerHtml())
                .as("bản đã lưu cũng phải sạch, không chỉ giá trị trả về")
                .doesNotContain("<script");
    }

    @Test
    void createComputesSearchKeywordsIncludingVietnameseSynonym() {
        Question created = useCase.create(new ManageQuestionsUseCase.CreateCommand(
                MODULE_ID, "Chiến lược sao lưu dữ liệu", "<p>nội dung</p>",
                Difficulty.MID, List.of("postgres"), null));

        // "sao lưu" → n-gram sao-luu → synonym backup/replication.
        assertThat(created.getSearchKeywords()).contains("sao-luu", "backup");
    }

    @Test
    void createAssignsNextSortOrderWhenNotProvided() {
        questions.seed(sampleQuestion(1));
        questions.seed(sampleQuestion(2));

        Question created = useCase.create(new ManageQuestionsUseCase.CreateCommand(
                MODULE_ID, "Câu mới", "<p>x</p>", Difficulty.MID, List.of(), null));

        assertThat(created.getSortOrder()).isEqualTo(3);
    }

    @Test
    void createRejectsDuplicateSortOrderWithConflict() {
        questions.seed(sampleQuestion(1));

        assertThatThrownBy(() -> useCase.create(new ManageQuestionsUseCase.CreateCommand(
                MODULE_ID, "Đè lên câu cũ", "<p>x</p>", Difficulty.MID, List.of(), 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1");
    }

    @Test
    void createFailsWhenModuleMissing() {
        ManageQuestionsUseCase orphan = new ManageQuestionsUseCase(
                questions, new InMemoryModuleRepository(), SANITIZER);

        assertThatThrownBy(() -> orphan.create(new ManageQuestionsUseCase.CreateCommand(
                UUID.randomUUID(), "Câu mồ côi", "<p>x</p>", Difficulty.MID, List.of(), null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createRejectsAnswerThatBecomesEmptyAfterSanitize() {
        assertThatThrownBy(() -> useCase.create(new ManageQuestionsUseCase.CreateCommand(
                MODULE_ID, "Chỉ có script", "<script>alert(1)</script>", Difficulty.MID, List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("answerHtml");
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateRecomputesSearchKeywordsWhenAnswerChanges() {
        Question seeded = questions.seed(sampleQuestion(1));

        useCase.update(seeded.getId(), new ManageQuestionsUseCase.UpdateCommand(
                null, "<p>So sánh replication lag giữa các node</p>", null, null));

        Question stored = questions.findById(seeded.getId()).orElseThrow();
        assertThat(stored.getSearchKeywords())
                .as("token từ answer mới phải vào index")
                .contains("replication", "lag")
                .as("token của answer cũ phải biến mất — nếu không, search trả kết quả lệch dữ liệu")
                .doesNotContain("zzoldtoken");
    }

    @Test
    void updateRecomputesSearchKeywordsWhenTagsChange() {
        Question seeded = questions.seed(sampleQuestion(1));

        useCase.update(seeded.getId(), new ManageQuestionsUseCase.UpdateCommand(
                null, null, null, List.of("sao-luu")));

        assertThat(questions.findById(seeded.getId()).orElseThrow().getSearchKeywords())
                .contains("sao-luu", "backup");
    }

    @Test
    void updateKeepsExistingKeywordsWhenNothingContentRelatedChanges() {
        Question seeded = questions.seed(sampleQuestion(1));
        List<String> before = List.copyOf(questions.findById(seeded.getId()).orElseThrow().getSearchKeywords());

        useCase.update(seeded.getId(), new ManageQuestionsUseCase.UpdateCommand(
                null, null, Difficulty.JUNIOR, null));

        Question stored = questions.findById(seeded.getId()).orElseThrow();
        assertThat(stored.getDifficulty()).isEqualTo(Difficulty.JUNIOR);
        assertThat(stored.getSearchKeywords()).isEqualTo(before);
    }

    @Test
    void updateSanitizesAnswerHtml() {
        Question seeded = questions.seed(sampleQuestion(1));

        useCase.update(seeded.getId(), new ManageQuestionsUseCase.UpdateCommand(
                null, "<p>ok</p><script>alert('xss')</script>", null, null));

        assertThat(questions.findById(seeded.getId()).orElseThrow().getAnswerHtml())
                .isEqualTo("<p>ok</p>");
    }

    @Test
    void updateFailsForUnknownId() {
        assertThatThrownBy(() -> useCase.update(UUID.randomUUID(),
                new ManageQuestionsUseCase.UpdateCommand("t", null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteRemovesExistingAndFailsForUnknownId() {
        Question seeded = questions.seed(sampleQuestion(1));

        useCase.delete(seeded.getId());
        assertThat(questions.findById(seeded.getId())).isEmpty();

        assertThatThrownBy(() -> useCase.delete(seeded.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ fakes

    private static Question sampleQuestion(int sortOrder) {
        Question question = new Question();
        question.setModuleId(MODULE_ID);
        question.setTitle("Câu cũ");
        question.setAnswerHtml("<p>zzoldtoken</p>");
        question.setDifficulty(Difficulty.MID);
        question.setTags(List.of("cu"));
        question.setSortOrder(sortOrder);
        return question;
    }

    private static final class InMemoryQuestionRepository implements QuestionRepository {

        private final Map<UUID, Question> store = new HashMap<>();

        /** Seed như thể dữ liệu đã được import — keywords tính đúng từ nội dung ban đầu. */
        Question seed(Question question) {
            question.setSearchKeywords(SearchText.tokenList(SearchText.build(
                    question.getTitle(), SearchText.stripHtml(question.getAnswerHtml()),
                    String.join(" ", question.getTags()))));
            return persist(question);
        }

        /**
         * Cố ý **không** tính lại keywords: `searchKeywords` là thứ use case phải set. Nếu fake
         * tự tính, test sẽ xanh kể cả khi use case quên recompute — mất hết giá trị kiểm chứng.
         */
        private Question persist(Question question) {
            if (question.getId() == null) {
                question.setId(UUID.randomUUID());
            }
            store.put(question.getId(), question);
            return question;
        }

        @Override
        public Optional<Question> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Question> findByModuleIdAndSortOrder(UUID moduleId, int sortOrder) {
            return store.values().stream()
                    .filter(q -> moduleId.equals(q.getModuleId()) && q.getSortOrder() == sortOrder)
                    .findFirst();
        }

        @Override
        public PageResult<Question> search(QuestionQuery query) {
            List<Question> items = new ArrayList<>(store.values());
            return new PageResult<>(items, query.page(), query.size(), items.size());
        }

        @Override
        public int saveOrUpdateByNaturalKey(List<Question> questions) {
            questions.forEach(this::persist);
            return questions.size();
        }

        @Override
        public int deleteAbsentSortOrders(UUID moduleId, Collection<Integer> keepSortOrders) {
            List<UUID> toRemove = store.values().stream()
                    .filter(q -> moduleId.equals(q.getModuleId()))
                    .filter(q -> keepSortOrders == null || !keepSortOrders.contains(q.getSortOrder()))
                    .map(Question::getId)
                    .toList();
            toRemove.forEach(store::remove);
            return toRemove.size();
        }

        @Override
        public Question save(Question question) {
            return persist(question);
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

    private static final class InMemoryModuleRepository implements ModuleRepository {

        @Override
        public Optional<ModuleRef> findBySlug(String slug) {
            return Optional.empty();
        }

        @Override
        public Optional<ModuleRef> findById(UUID id) {
            return MODULE_ID.equals(id) ? Optional.of(module()) : Optional.empty();
        }

        @Override
        public ModuleRef saveOrUpdateBySlug(ModuleRef module) {
            return module;
        }

        @Override
        public List<ModuleRef> findByTopicIdOrdered(UUID topicId) {
            return List.of();
        }

        @Override
        public List<ModuleRef> findAllOrdered() {
            return List.of(module());
        }

        @Override
        public long countQuestions(UUID moduleId) {
            return 0;
        }

        @Override
        public Map<UUID, Long> countQuestionsByModule() {
            return Map.of();
        }

        private static ModuleRef module() {
            ModuleRef module = new ModuleRef("Test Module", "01-java-core", 1);
            module.setId(MODULE_ID);
            return module;
        }
    }
}
