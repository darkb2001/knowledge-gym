package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.learning.application.LearningTestSupport.InMemoryModuleRepository;
import com.knowledgegym.learning.application.LearningTestSupport.InMemoryQuestionRepository;
import com.knowledgegym.learning.application.LearningTestSupport.InMemorySRSCardRepository;
import com.knowledgegym.learning.application.LearningTestSupport.InMemorySrsDeckRepository;
import com.knowledgegym.learning.domain.model.SrsDeck;
import com.knowledgegym.shared.application.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enroll dual-mode + idempotency. Trọng tâm:
 * 1. Gọi 2 lần cùng module không tạo thẻ trùng **và** không tạo deck trùng (V004 không có UK
 *    `(user_id, module_id)`, application là hàng rào duy nhất).
 * 2. Re-enroll không reset lịch ôn của thẻ đã có.
 * 3. Gửi cả `moduleId` và `questionIds`, hoặc không gửi gì, đều là 400.
 */
class EnrollCardsUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);

    private InMemoryQuestionRepository questions;
    private InMemoryModuleRepository modules;
    private InMemorySRSCardRepository cards;
    private InMemorySrsDeckRepository decks;
    private EnrollCardsUseCase useCase;

    @BeforeEach
    void setUp() {
        questions = new InMemoryQuestionRepository();
        modules = new InMemoryModuleRepository();
        cards = new InMemorySRSCardRepository();
        decks = new InMemorySrsDeckRepository();
        useCase = new EnrollCardsUseCase(questions, modules, cards, decks, CLOCK);

        ModuleRef module = new ModuleRef("Java Core", "01-java-core", 1);
        module.setId(MODULE_ID);
        modules.seed(module);
    }

    // ------------------------------------------------------------------ mode A

    @Test
    void enrollByModuleCreatesCardForEveryQuestionAndAutoDeck() {
        seedQuestions(3);

        EnrollCardsUseCase.EnrollResult result =
                useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        assertThat(result.enrolled()).isEqualTo(3);
        assertThat(result.cardIds()).hasSize(3);
        assertThat(cards.countByUserId(USER_ID)).isEqualTo(3);

        SrsDeck deck = decks.store.get(result.deckId());
        assertThat(deck).isNotNull();
        assertThat(deck.getModuleId()).isEqualTo(MODULE_ID);
        assertThat(deck.getName()).as("tên deck lấy từ tên module").isEqualTo("Java Core");
        assertThat(deck.isCustom()).isFalse();
        assertThat(cards.store.values())
                .allSatisfy(card -> assertThat(card.getDeckId()).isEqualTo(deck.getId()));
    }

    @Test
    void secondEnrollOfSameModuleAddsNoCardsAndNoDeck() {
        seedQuestions(3);
        useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        EnrollCardsUseCase.EnrollResult second =
                useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        assertThat(second.enrolled()).as("UK (user_id, question_id) chặn trùng").isZero();
        assertThat(second.cardIds()).isEmpty();
        assertThat(cards.countByUserId(USER_ID)).isEqualTo(3);
        assertThat(decks.store).as("deck auto phải được reuse, không tạo thêm").hasSize(1);
        assertThat(second.deckId()).isEqualTo(decks.store.keySet().iterator().next());
    }

    @Test
    void reEnrollDoesNotResetScheduleOfExistingCards() {
        seedQuestions(1);
        useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        UUID cardId = cards.store.keySet().iterator().next();
        var card = cards.store.get(cardId);
        card.applyReview(2, LocalDate.of(2026, 3, 10), Instant.parse("2026-03-10T09:00:00Z"));
        LocalDate nextReview = card.getNextReview();

        useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        assertThat(cards.store.get(cardId).getNextReview())
                .as("enroll lại không được đụng vào lịch ôn")
                .isEqualTo(nextReview);
        assertThat(cards.store.get(cardId).getRepetitions()).isEqualTo(1);
    }

    @Test
    void enrollingNewQuestionInModuleKeepsSameDeck() {
        seedQuestions(1);
        UUID deckId = useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null)).deckId();

        questions.seed(LearningTestSupport.question(MODULE_ID, "Câu mới", 99));
        EnrollCardsUseCase.EnrollResult second = useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        assertThat(second.enrolled()).isEqualTo(1);
        assertThat(second.deckId()).isEqualTo(deckId);
        assertThat(decks.store).hasSize(1);
    }

    @Test
    void emptyModuleEnrollsNothingAndCreatesNoDeck() {
        EnrollCardsUseCase.EnrollResult result =
                useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        assertThat(result.enrolled()).isZero();
        assertThat(result.deckId()).isNull();
        assertThat(decks.store).as("module chưa có câu thì không tạo deck rỗng").isEmpty();
    }

    @Test
    void enrollByModulePaginatesBeyondOnePage() {
        // MAX_SIZE = 100 → 101 câu phải lấy đủ qua 2 trang, không bị cắt im lặng.
        for (int i = 1; i <= 101; i++) {
            questions.seed(LearningTestSupport.question(MODULE_ID, "Câu " + i, i));
        }

        EnrollCardsUseCase.EnrollResult result =
                useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        assertThat(result.enrolled()).isEqualTo(101);
        assertThat(QuestionQuery.MAX_SIZE).isEqualTo(100);
    }

    @Test
    void unknownModuleReturnsNotFound() {
        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(UUID.randomUUID(), null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ mode B

    @Test
    void enrollByQuestionIdsWithoutDeckLeavesCardsDeckless() {
        List<Question> seeded = seedQuestions(2);

        EnrollCardsUseCase.EnrollResult result = useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, seeded.stream().map(Question::getId).toList(), null));

        assertThat(result.enrolled()).isEqualTo(2);
        assertThat(result.deckId()).isNull();
        assertThat(cards.store.values()).allSatisfy(card -> assertThat(card.getDeckId()).isNull());
        assertThat(decks.store).as("mode B không auto tạo deck").isEmpty();
    }

    @Test
    void enrollByQuestionIdsWithOwnDeckAssignsCardsToIt() {
        UUID deckId = decks.save(SrsDeck.custom(USER_ID, "Câu hay quên")).getId();
        List<UUID> questionIds = seedQuestions(2).stream().map(Question::getId).toList();

        EnrollCardsUseCase.EnrollResult result = useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, questionIds, deckId));

        assertThat(result.deckId()).isEqualTo(deckId);
        assertThat(cards.store.values()).allSatisfy(card -> assertThat(card.getDeckId()).isEqualTo(deckId));
    }

    @Test
    void deckIdFromAnotherUserIsRejected() {
        UUID foreignDeck = decks.save(SrsDeck.custom(UUID.randomUUID(), "Deck người khác")).getId();
        List<UUID> questionIds = seedQuestions(1).stream().map(Question::getId).toList();

        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, questionIds, foreignDeck)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(foreignDeck.toString());
    }

    @Test
    void duplicateQuestionIdsInRequestCreateOneCardEach() {
        UUID questionId = seedQuestions(1).get(0).getId();

        EnrollCardsUseCase.EnrollResult result = useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, List.of(questionId, questionId), null));

        assertThat(result.enrolled()).isEqualTo(1);
        assertThat(cards.countByUserId(USER_ID)).isEqualTo(1);
    }

    @Test
    void enrollingNonpublicIdsRejectsTheEntireRequestBeforeWritingAnyCards() {
        List<Question> seeded = seedQuestions(2);
        for (var status : Question.ContentStatus.values()) {
            if (status == Question.ContentStatus.PUBLISHED) continue;
            seeded.get(1).setContentStatus(status);
            assertThatThrownBy(() -> useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(
                    null, seeded.stream().map(Question::getId).toList(), null)))
                    .isInstanceOf(NotFoundException.class);
            assertThat(cards.store).isEmpty();
            assertThat(decks.store).isEmpty();
        }
    }

    @Test
    void unknownQuestionIdReturnsNotFoundInsteadOfForeignKeyViolation() {
        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, List.of(UUID.randomUUID()), null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Câu hỏi không tồn tại");
    }

    @Test
    void nullQuestionIdElementIsRejectedAsBadRequestNotServerError() {
        // `List.copyOf` trong dedupe sẽ ném NullPointerException (không có handler → 500) nếu không
        // chặn tường minh. Input sai định dạng của client phải là 400.
        List<UUID> withNull = new ArrayList<>();
        withNull.add(UUID.randomUUID());
        withNull.add(null);

        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, withNull, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void questionIdsBeyondCapAreRejected() {
        // Cap chặn request khuếch đại: mỗi id là một phần tử của câu INSERT, không giới hạn thì
        // một request có thể mang hàng chục nghìn id trước khi trả lỗi.
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i <= EnrollCardsUseCase.MAX_QUESTION_IDS; i++) {
            tooMany.add(UUID.randomUUID());
        }

        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, tooMany, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá");
    }

    @Test
    void questionIdsAtCapAreAccepted() {
        List<Question> seeded = new ArrayList<>();
        for (int i = 0; i < EnrollCardsUseCase.MAX_QUESTION_IDS; i++) {
            Question question = LearningTestSupport.question(MODULE_ID, "Câu " + i, i + 1);
            questions.seed(question);
            seeded.add(question);
        }

        EnrollCardsUseCase.EnrollResult result = useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null,
                        seeded.stream().map(Question::getId).toList(), null));

        assertThat(result.enrolled()).isEqualTo(EnrollCardsUseCase.MAX_QUESTION_IDS);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void sendingBothModesIsRejected() {
        List<UUID> questionIds = seedQuestions(1).stream().map(Question::getId).toList();

        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(MODULE_ID, questionIds, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ gửi một trong hai");
    }

    @Test
    void sendingNeitherModeIsRejected() {
        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("moduleId hoặc questionIds");
    }

    @Test
    void emptyQuestionIdListIsTreatedAsMissingMode() {
        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(null, List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deckIdIsRejectedInModuleMode() {
        assertThatThrownBy(() -> useCase.execute(USER_ID,
                new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deckId chỉ dùng");
    }

    @Test
    void twoUsersEnrollingSameModuleGetSeparateDecksAndCards() {
        seedQuestions(2);
        UUID otherUser = UUID.randomUUID();

        EnrollCardsUseCase.EnrollResult mine =
                useCase.execute(USER_ID, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));
        EnrollCardsUseCase.EnrollResult theirs =
                useCase.execute(otherUser, new EnrollCardsUseCase.EnrollCommand(MODULE_ID, null, null));

        assertThat(mine.deckId()).isNotEqualTo(theirs.deckId());
        assertThat(decks.store).hasSize(2);
        assertThat(cards.countByUserId(USER_ID)).isEqualTo(2);
        assertThat(cards.countByUserId(otherUser)).isEqualTo(2);
    }

    private List<Question> seedQuestions(int count) {
        List<Question> seeded = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Question question = LearningTestSupport.question(MODULE_ID, "Câu " + i, i);
            questions.seed(question);
            seeded.add(question);
        }
        return seeded;
    }
}
