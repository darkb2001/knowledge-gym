package com.knowledgegym.learning.domain.model;

import com.knowledgegym.learning.domain.service.Sm2Scheduler;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariant của deck không có CHECK constraint ở V004, nên factory là hàng rào duy nhất —
 * test này chứng minh không tạo được row lệch `is_custom != (module_id == null)`.
 */
class SrsDeckTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();

    @Test
    void moduleDeckIsNotCustomAndCarriesModuleId() {
        SrsDeck deck = SrsDeck.forModule(USER_ID, MODULE_ID, "  Java Core  ");

        assertThat(deck.getModuleId()).isEqualTo(MODULE_ID);
        assertThat(deck.isCustom()).isFalse();
        assertThat(deck.isForModule()).isTrue();
        assertThat(deck.getName()).as("tên được trim").isEqualTo("Java Core");
    }

    @Test
    void customDeckHasNullModuleIdAndFlagSet() {
        SrsDeck deck = SrsDeck.custom(USER_ID, "Câu hay quên");

        assertThat(deck.getModuleId()).isNull();
        assertThat(deck.isCustom()).isTrue();
        assertThat(deck.isForModule()).isFalse();
    }

    @Test
    void moduleDeckRequiresModuleId() {
        assertThatThrownBy(() -> SrsDeck.forModule(USER_ID, null, "deck"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("moduleId");
    }

    @Test
    void blankNameIsRejectedForBothKinds() {
        assertThatThrownBy(() -> SrsDeck.forModule(USER_ID, MODULE_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SrsDeck.custom(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userIdIsRequired() {
        assertThatThrownBy(() -> SrsDeck.custom(null, "deck"))
                .isInstanceOf(NullPointerException.class);
    }

    /** Mọi factory đều suy `is_custom` từ `moduleId`, nên row lệch chỉ lọt vào qua `rehydrate`. */
    @Test
    void rehydrateAcceptsConsistentRows() {
        SrsDeck moduleDeck = SrsDeck.rehydrate(UUID.randomUUID(), USER_ID, "Java Core", MODULE_ID, false);
        assertThat(moduleDeck.isForModule()).isTrue();
        assertThat(moduleDeck.isCustom()).isFalse();

        SrsDeck customDeck = SrsDeck.rehydrate(UUID.randomUUID(), USER_ID, "Câu hay quên", null, true);
        assertThat(customDeck.isForModule()).isFalse();
        assertThat(customDeck.isCustom()).isTrue();
    }

    @Test
    void rehydrateRejectsModuleDeckMarkedCustom() {
        assertThatThrownBy(() -> SrsDeck.rehydrate(UUID.randomUUID(), USER_ID, "deck", MODULE_ID, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invariant");
    }

    @Test
    void rehydrateRejectsCustomDeckCarryingModuleId() {
        assertThatThrownBy(() -> SrsDeck.rehydrate(UUID.randomUUID(), USER_ID, "deck", null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invariant");
    }
}

/** Card mới đến hạn ngay; mọi thay đổi lịch đi qua `applyReview` nên không thể lệch trạng thái. */
class SRSCardTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID QUESTION_ID = UUID.randomUUID();
    private static final UUID DECK_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    @Test
    void newCardIsDueTodayWithDefaultEase() {
        SRSCard card = SRSCard.newCard(USER_ID, QUESTION_ID, DECK_ID, null, TODAY);

        assertThat(card.getNextReview()).isEqualTo(TODAY);
        assertThat(card.getRepetitions()).isZero();
        assertThat(card.getIntervalDays()).isZero();
        assertThat(card.getEaseFactor()).isEqualTo(Sm2Scheduler.DEFAULT_EASE_FACTOR);
        assertThat(card.getLastReviewedAt()).isNull();
        assertThat(card.isDueOn(TODAY)).isTrue();
    }

    @Test
    void applyReviewMovesStateAndStampsLastReviewedAt() {
        SRSCard card = SRSCard.newCard(USER_ID, QUESTION_ID, DECK_ID, null, TODAY);
        Instant reviewedAt = Instant.parse("2026-03-10T09:00:00Z");

        Sm2Scheduler.Schedule schedule = card.applyReview(Sm2Scheduler.QUALITY_GOOD, TODAY, reviewedAt);

        assertThat(schedule.intervalDays()).isEqualTo(1);
        assertThat(card.getIntervalDays()).isEqualTo(1);
        assertThat(card.getRepetitions()).isEqualTo(1);
        assertThat(card.getNextReview()).isEqualTo(TODAY.plusDays(1));
        assertThat(card.getLastReviewedAt()).isEqualTo(reviewedAt);
        assertThat(card.isDueOn(TODAY)).as("sau khi ôn thì không còn due hôm nay").isFalse();
    }

    @Test
    void reviewSequenceUsesCardStateNotRequestValues() {
        SRSCard card = SRSCard.newCard(USER_ID, QUESTION_ID, DECK_ID, null, TODAY);
        Instant now = Instant.parse("2026-03-10T09:00:00Z");

        card.applyReview(Sm2Scheduler.QUALITY_GOOD, TODAY, now);
        card.applyReview(Sm2Scheduler.QUALITY_GOOD, TODAY.plusDays(1), now);

        // 1 → ceil(1 × 2.5) = 3, chứng minh interval của lần sau đọc từ state đã lưu.
        assertThat(card.getIntervalDays()).isEqualTo(3);
        assertThat(card.getRepetitions()).isEqualTo(2);
    }

    @Test
    void unknownQualityIsRejectedWithoutMutatingCard() {
        SRSCard card = SRSCard.newCard(USER_ID, QUESTION_ID, DECK_ID, null, TODAY);
        Instant now = Instant.parse("2026-03-10T09:00:00Z");

        assertThatThrownBy(() -> card.applyReview(9, TODAY, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(card.getNextReview()).as("thẻ không đổi khi input sai").isEqualTo(TODAY);
        assertThat(card.getLastReviewedAt()).isNull();
    }

    @Test
    void newCardRequiresUserAndQuestion() {
        assertThatThrownBy(() -> SRSCard.newCard(null, QUESTION_ID, DECK_ID, null, TODAY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SRSCard.newCard(USER_ID, null, DECK_ID, null, TODAY))
                .isInstanceOf(NullPointerException.class);
    }
}
