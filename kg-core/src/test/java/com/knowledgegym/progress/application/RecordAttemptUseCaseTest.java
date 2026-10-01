package com.knowledgegym.progress.application;

import com.knowledgegym.learning.domain.model.AttemptSource;
import com.knowledgegym.learning.domain.model.StudyAttempt;
import com.knowledgegym.progress.domain.model.UserProgress;
import com.knowledgegym.progress.domain.service.UserXpPolicy;
import com.knowledgegym.shared.application.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hợp đồng của đường ghi duy nhất: attempt + `user_progress` + XP phải nhất quán, XP chỉ cộng cho
 * **lần đầu của mỗi câu**, và mastery phải tính từ tổng luỹ kế.
 */
class RecordAttemptUseCaseTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID MODULE = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-10-01T10:00:00Z");

    private ProgressTestSupport.InMemoryStudyAttemptRepository attempts;
    private ProgressTestSupport.InMemoryQuestionModulePort questionModules;
    private ProgressTestSupport.XpAndLockRecorder xp;
    private ProgressTestSupport.InMemoryProgressRepository progress;
    private RecordAttemptUseCase useCase;

    @BeforeEach
    void setUp() {
        attempts = new ProgressTestSupport.InMemoryStudyAttemptRepository();
        questionModules = new ProgressTestSupport.InMemoryQuestionModulePort();
        xp = new ProgressTestSupport.XpAndLockRecorder(attempts.store);
        progress = new ProgressTestSupport.InMemoryProgressRepository();
        useCase = new RecordAttemptUseCase(attempts, questionModules, xp, progress);
    }

    private UUID question(UUID moduleId) {
        UUID id = UUID.randomUUID();
        questionModules.seed(id, moduleId);
        return id;
    }

    private static RecordAttemptUseCase.AttemptFact fact(UUID questionId, AttemptSource source, boolean correct) {
        return new RecordAttemptUseCase.AttemptFact(questionId, source, correct);
    }

    /**
     * `attempted_at` phải bằng **đúng** thời điểm caller truyền vào: `SubmitQuizUseCase` dùng cùng
     * `Instant` cho `session.finish` và attempt, và có test khoá bất biến đó ở tầng API.
     */
    @Test
    void attemptsCarryTheCallerSuppliedTimestamp() {
        UUID q = question(MODULE);

        useCase.execute(USER, List.of(fact(q, AttemptSource.PRACTICE, true)), AT);

        assertThat(attempts.store).hasSize(1);
        assertThat(attempts.store.get(0).getAttemptedAt()).isEqualTo(AT);
        assertThat(progress.findAllByUser(USER).get(0).lastActiveAt()).isEqualTo(AT);
    }

    @Test
    void firstAttemptAwardsXpAndLaterAttemptsOfSameQuestionDoNot() {
        UUID q = question(MODULE);

        useCase.execute(USER, List.of(fact(q, AttemptSource.FLASHCARD, true)), AT);
        assertThat(xp.currentXp(USER)).isEqualTo(UserXpPolicy.xpFor(AttemptSource.FLASHCARD, true));

        useCase.execute(USER, List.of(fact(q, AttemptSource.FLASHCARD, true)), AT.plusSeconds(60));
        useCase.execute(USER, List.of(fact(q, AttemptSource.PRACTICE, true)), AT.plusSeconds(120));

        assertThat(xp.currentXp(USER))
                .as("chỉ lần đầu của câu mới cộng XP")
                .isEqualTo(UserXpPolicy.xpFor(AttemptSource.FLASHCARD, true));
    }

    /**
     * Câu hỏi P5: 1 flashcard + 1 quiz cùng là "lần đầu" của một câu trong 2 tx. Khoá advisory đã
     * serialize ở DB thật; ở tầng này kiểm chứng rằng truy vấn "đã từng attempt chưa" được đọc
     * **sau** tất cả lock, nên request thứ hai thấy attempt của request thứ nhất.
     */
    @Test
    void secondRequestSeesFirstAttemptBecauseItReadsAfterLocks() {
        UUID q = question(MODULE);

        useCase.execute(USER, List.of(fact(q, AttemptSource.FLASHCARD, true)), AT);
        assertThat(xp.lockOrder).containsExactly(q);

        int xpAfterFirst = xp.currentXp(USER);
        useCase.execute(USER, List.of(fact(q, AttemptSource.PRACTICE, true)), AT.plusSeconds(1));

        assertThat(xp.currentXp(USER)).isEqualTo(xpAfterFirst);
        assertThat(attempts.store).hasSize(2);
    }

    @Test
    void locksAreTakenInSortedQuestionOrderToAvoidDeadlock() {
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        List.of(q1, q2, q3).forEach(id -> questionModules.seed(id, MODULE));

        useCase.execute(USER, List.of(fact(q3, AttemptSource.PRACTICE, true),
                fact(q1, AttemptSource.PRACTICE, true),
                fact(q2, AttemptSource.PRACTICE, true)), AT);

        List<UUID> sorted = List.of(q1, q2, q3).stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        assertThat(xp.lockOrder).isEqualTo(sorted);
    }

    /**
     * `mastery_pct` phải tính từ tổng luỹ kế, không phải từ delta. Ghi 2 lần với delta không đều;
     * nếu công thức dùng delta thì lần thứ hai sẽ ra 100% thay vì 50%.
     */
    @Test
    void masteryAccumulatesAcrossWrites() {
        UUID q1 = question(MODULE);
        UUID q2 = question(MODULE);

        useCase.execute(USER, List.of(fact(q1, AttemptSource.PRACTICE, true)), AT);
        UserProgress afterFirst = progress.find(USER, MODULE).orElseThrow();
        assertThat(afterFirst.totalAttempts()).isEqualTo(1);
        assertThat(afterFirst.correctCount()).isEqualTo(1);
        assertThat(afterFirst.masteryPct()).isEqualByComparingTo("100.00");

        useCase.execute(USER, List.of(fact(q2, AttemptSource.PRACTICE, false)), AT.plusSeconds(1));
        UserProgress afterSecond = progress.find(USER, MODULE).orElseThrow();
        assertThat(afterSecond.totalAttempts()).isEqualTo(2);
        assertThat(afterSecond.correctCount()).isEqualTo(1);
        assertThat(afterSecond.masteryPct())
                .as("1/2 luỹ kế, không phải 0/1 của delta")
                .isEqualByComparingTo("50.00");
    }

    @Test
    void attemptsInOneCallAreAggregatedPerModule() {
        UUID q1 = question(MODULE);
        UUID q2 = question(MODULE);

        useCase.execute(USER, List.of(fact(q1, AttemptSource.PRACTICE, true),
                fact(q2, AttemptSource.PRACTICE, false)), AT);

        assertThat(progress.upsertCalls).as("1 upsert/module, không phải 1 upsert/câu").isEqualTo(1);
        assertThat(progress.find(USER, MODULE).orElseThrow().totalAttempts()).isEqualTo(2);
    }

    @Test
    void multipleModulesAreUpsertedSeparately() {
        UUID otherModule = UUID.randomUUID();
        UUID q1 = question(MODULE);
        UUID q2 = question(otherModule);

        useCase.execute(USER, List.of(fact(q1, AttemptSource.PRACTICE, true),
                fact(q2, AttemptSource.PRACTICE, true)), AT);

        assertThat(progress.upsertCalls).isEqualTo(2);
        assertThat(progress.find(USER, MODULE)).isPresent();
        assertThat(progress.find(USER, otherModule)).isPresent();
    }

    @Test
    void attemptAndProgressAgreeOnTheSameTransactionBoundary() {
        UUID q = question(MODULE);

        useCase.execute(USER, List.of(fact(q, AttemptSource.DAILY, true)), AT);

        assertThat(attempts.store).hasSize(1);
        assertThat(progress.findAllByUser(USER)).hasSize(1);
        assertThat(xp.currentXp(USER)).isEqualTo(UserXpPolicy.xpFor(AttemptSource.DAILY, true));
    }

    /** Hai đường tính XP (ghi tăng dần vs recompute từ attempt) phải ra cùng kết quả. */
    @Test
    void incrementalXpMatchesRecomputedXp() {
        UUID q1 = question(MODULE);
        UUID q2 = question(MODULE);
        UUID q3 = question(MODULE);

        useCase.execute(USER, List.of(fact(q1, AttemptSource.FLASHCARD, true)), AT);
        useCase.execute(USER, List.of(fact(q1, AttemptSource.FLASHCARD, true)), AT.plusSeconds(1));
        useCase.execute(USER, List.of(fact(q2, AttemptSource.PRACTICE, false)), AT.plusSeconds(2));
        useCase.execute(USER, List.of(fact(q3, AttemptSource.DAILY, true)), AT.plusSeconds(3));

        assertThat(xp.currentXp(USER))
                .as("users.xp phải khớp recompute từ study_attempts")
                .isEqualTo(xp.xpOf(USER));
    }

    @Test
    void backdatedAttemptAdjustsXpToMatchTheCanonicalEarliestAttempt() {
        UUID q = question(MODULE);
        useCase.execute(USER, List.of(fact(q, AttemptSource.FLASHCARD, true)), AT);
        assertThat(xp.currentXp(USER)).isEqualTo(10);

        useCase.execute(USER, List.of(fact(q, AttemptSource.PRACTICE, false)), AT.minusSeconds(1));

        assertThat(xp.currentXp(USER)).as("earlier wrong attempt replaces the 10 XP first attempt with 1 XP")
                .isEqualTo(1);
        assertThat(xp.currentXp(USER)).isEqualTo(xp.xpOf(USER));
    }

    @Test
    void submissionWithOneWrongAnswerOnlyAwardsXpForTheNewQuestions() {
        UUID freshlyWrong = question(MODULE);
        UUID previouslyAnswered = question(MODULE);

        useCase.execute(USER, List.of(fact(previouslyAnswered, AttemptSource.PRACTICE, true)), AT);
        int xpBefore = xp.currentXp(USER);
        int addCallsBefore = xp.addXpCalls;

        useCase.execute(USER, List.of(
                fact(previouslyAnswered, AttemptSource.PRACTICE, false),
                fact(freshlyWrong, AttemptSource.PRACTICE, false)), AT.plusSeconds(1));

        assertThat(xp.currentXp(USER))
                .isEqualTo(xpBefore + UserXpPolicy.xpFor(AttemptSource.PRACTICE, false));
        assertThat(xp.addXpCalls).isGreaterThan(addCallsBefore);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void duplicateQuestionInOneCallIsRejected() {
        UUID q = question(MODULE);

        assertThatThrownBy(() -> useCase.execute(USER, List.of(
                fact(q, AttemptSource.PRACTICE, true),
                fact(q, AttemptSource.PRACTICE, false)), AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trùng");

        assertThat(attempts.store).isEmpty();
        assertThat(progress.upsertCalls).isZero();
    }

    @Test
    void unknownQuestionModuleIsRejectedBeforeWriting() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(USER, List.of(fact(unknown, AttemptSource.PRACTICE, true)), AT))
                .isInstanceOf(NotFoundException.class);

        assertThat(attempts.store).isEmpty();
        assertThat(xp.currentXp(USER)).isZero();
    }

    @Test
    void negativeTimeMsIsRejected() {
        UUID q = question(MODULE);

        assertThatThrownBy(() -> useCase.execute(USER, List.of(new RecordAttemptUseCase.AttemptFact(
                q, AttemptSource.PRACTICE, true, -1, null, null)), AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeMs");

        assertThat(attempts.store).isEmpty();
    }

    @Test
    void emptyFactsIsANoOp() {
        useCase.execute(USER, List.of(), AT);
        useCase.execute(USER, null, AT);

        assertThat(attempts.store).isEmpty();
        assertThat(progress.upsertCalls).isZero();
        assertThat(xp.lockOrder).isEmpty();
    }

    /** Quiz nộp lại: `answer`/`score` của m6 phải được giữ nguyên qua đường ghi mới. */
    @Test
    void answerAndScoreArePersisted() {
        UUID q = question(MODULE);
        BigDecimal score = BigDecimal.valueOf(100);

        useCase.execute(USER, List.of(new RecordAttemptUseCase.AttemptFact(
                q, AttemptSource.PRACTICE, true, 1500, "option-id", score)), AT);

        StudyAttempt saved = attempts.store.get(0);
        assertThat(saved.getAnswer()).isEqualTo("option-id");
        assertThat(saved.getScore()).isEqualByComparingTo(score);
        assertThat(saved.getTimeMs()).isEqualTo(1500);
    }

    @Test
    void flashcardAttemptLeavesAnswerAndScoreNull() {
        UUID q = question(MODULE);

        useCase.execute(USER, List.of(fact(q, AttemptSource.FLASHCARD, true)), AT);

        StudyAttempt saved = attempts.store.get(0);
        assertThat(saved.getAnswer()).isNull();
        assertThat(saved.getScore()).as("score là thang điểm quiz (m6)").isNull();
    }
}
