package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.learning.application.LearningTestSupport.InMemoryModuleRepository;
import com.knowledgegym.learning.application.LearningTestSupport.InMemoryQuestionRepository;
import com.knowledgegym.learning.application.LearningTestSupport.InMemorySRSCardRepository;
import com.knowledgegym.learning.domain.model.SRSCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query due: thứ tự sắp xếp, filter module áp **trước** limit, và thẻ mồ côi (câu bị re-import
 * xoá) không được trả về.
 */
class QueryDueUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MODULE_A = UUID.randomUUID();
    private static final UUID MODULE_B = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private InMemoryQuestionRepository questions;
    private InMemoryModuleRepository modules;
    private InMemorySRSCardRepository cards;
    private QueryDueUseCase useCase;

    @BeforeEach
    void setUp() {
        questions = new InMemoryQuestionRepository();
        modules = new InMemoryModuleRepository();
        cards = new InMemorySRSCardRepository();
        useCase = new QueryDueUseCase(cards, questions, modules, CLOCK);

        modules.seed(module(MODULE_A, "01-java-core"));
        modules.seed(module(MODULE_B, "02-multithreading"));
    }

    @Test
    void returnsDueCardsWithQuestionContentAndModuleSlug() {
        Question question = seedQuestion(MODULE_A, "Câu A", 1);
        seedCard(question, TODAY);

        List<QueryDueUseCase.DueCard> due = useCase.execute(USER_ID, null, null);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).question().getId()).isEqualTo(question.getId());
        assertThat(due.get(0).question().getAnswerHtml()).isEqualTo("<p>Câu A</p>");
        assertThat(due.get(0).moduleSlug()).isEqualTo("01-java-core");
    }

    @Test
    void excludesCardsScheduledForFutureAndOtherUsers() {
        Question mine = seedQuestion(MODULE_A, "Hôm nay", 1);
        seedCard(mine, TODAY);

        Question later = seedQuestion(MODULE_A, "Tuần sau", 2);
        seedCard(later, TODAY.plusDays(7));

        Question otherUser = seedQuestion(MODULE_A, "Của người khác", 3);
        cards.store.put(otherUser.getId(), SRSCard.newCard(UUID.randomUUID(), otherUser.getId(), null, null, TODAY));

        List<QueryDueUseCase.DueCard> due = useCase.execute(USER_ID, null, null);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).question().getId()).isEqualTo(mine.getId());
    }

    @Test
    void overdueCardsComeBeforeCardsDueToday() {
        Question today = seedQuestion(MODULE_A, "Đến hạn hôm nay", 1);
        seedCard(today, TODAY);

        Question overdue = seedQuestion(MODULE_A, "Quá hạn 5 ngày", 2);
        seedCard(overdue, TODAY.minusDays(5));

        List<QueryDueUseCase.DueCard> due = useCase.execute(USER_ID, null, null);

        assertThat(due).extracting(c -> c.question().getTitle())
                .containsExactly("Quá hạn 5 ngày", "Đến hạn hôm nay");
    }

    @Test
    void moduleFilterIsAppliedBeforeLimitSoResultIsNotShort() {
        // 3 thẻ module B quá hạn lâu hơn, 1 thẻ module A. Nếu LIMIT ở SQL chạy trước filter,
        // limit=1 sẽ trả thẻ module B rồi bị lọc bỏ → kết quả rỗng dù module A có thẻ đến hạn.
        for (int i = 1; i <= 3; i++) {
            seedCard(seedQuestion(MODULE_B, "B" + i, i), TODAY.minusDays(10));
        }
        Question a = seedQuestion(MODULE_A, "A1", 10);
        seedCard(a, TODAY);

        List<QueryDueUseCase.DueCard> due = useCase.execute(USER_ID, MODULE_A, 1);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).question().getModuleId()).isEqualTo(MODULE_A);
    }

    @Test
    void orphanCardsWhoseQuestionWasDeletedAreSkipped() {
        Question existing = seedQuestion(MODULE_A, "Còn tồn tại", 1);
        seedCard(existing, TODAY);

        // Thẻ trỏ tới câu không còn trong DB (re-import đã xoá) — không được trả DTO thiếu nội dung.
        seedCardRaw(UUID.randomUUID(), TODAY);

        List<QueryDueUseCase.DueCard> due = useCase.execute(USER_ID, null, null);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).question().getId()).isEqualTo(existing.getId());
    }

    @Test
    void limitIsCappedAndDefaultsToTwenty() {
        for (int i = 1; i <= 120; i++) {
            seedCard(seedQuestion(MODULE_A, "Câu " + i, i), TODAY);
        }

        assertThat(useCase.execute(USER_ID, null, null)).hasSize(QueryDueUseCase.DEFAULT_LIMIT);
        assertThat(useCase.execute(USER_ID, null, 0)).hasSize(QueryDueUseCase.DEFAULT_LIMIT);
        assertThat(useCase.execute(USER_ID, null, -5)).hasSize(QueryDueUseCase.DEFAULT_LIMIT);
        assertThat(useCase.execute(USER_ID, null, 100_000)).hasSize(QueryDueUseCase.MAX_LIMIT);
        assertThat(useCase.execute(USER_ID, null, 5)).hasSize(5);
    }

    @Test
    void noDueCardsReturnsEmptyWithoutTouchingQuestions() {
        assertThat(useCase.execute(USER_ID, null, null)).isEmpty();
    }

    private Question seedQuestion(UUID moduleId, String title, int sortOrder) {
        Question question = LearningTestSupport.question(moduleId, title, sortOrder);
        questions.seed(question);
        return question;
    }

    private void seedCard(Question question, LocalDate nextReview) {
        seedCardRaw(question.getId(), nextReview);
    }

    private void seedCardRaw(UUID questionId, LocalDate nextReview) {
        SRSCard card = SRSCard.newCard(USER_ID, questionId, null, null, nextReview);
        cards.store.put(card.getId(), card);
    }

    private static ModuleRef module(UUID id, String slug) {
        ModuleRef module = new ModuleRef(slug, slug, 1);
        module.setId(id);
        return module;
    }
}
