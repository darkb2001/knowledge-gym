package com.knowledgegym.content.domain.model;

import com.knowledgegym.shared.domain.model.Difficulty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hồi quy cho lỗi tham số đã gặp thật trên API: `page=Integer.MAX_VALUE` làm offset `int` tràn âm
 * → Postgres từ chối → API trả **409 "Resource already exists"**, message chẳng liên quan gì tới
 * tham số client gửi.
 *
 * Việc chuẩn hoá `q` không nằm ở record này (xem {@code QueryQuestionsUseCaseTest}) — record chỉ
 * chịu trách nhiệm clamp tham số phân trang.
 */
class QuestionQueryTest {

    private static QuestionQuery of(Integer page, Integer size) {
        return new QuestionQuery(null, Difficulty.MID, null, null,
                page == null ? 1 : page, size == null ? 20 : size);
    }

    @Test
    void pageBelowOneIsClampedToOne() {
        assertThat(of(0, 20).page()).isEqualTo(1);
        assertThat(of(-5, 20).page()).isEqualTo(1);
    }

    @Test
    void sizeIsClampedToValidRange() {
        assertThat(of(1, 0).size()).isEqualTo(QuestionQuery.DEFAULT_SIZE);
        assertThat(of(1, -1).size()).isEqualTo(QuestionQuery.DEFAULT_SIZE);
        assertThat(of(1, 99_999).size()).isEqualTo(QuestionQuery.MAX_SIZE);
        assertThat(of(1, null).size()).isEqualTo(QuestionQuery.DEFAULT_SIZE);
    }

    /** Giá trị client thật đã gửi và làm API trả 409. */
    @Test
    void integerMaxValuePageIsClampedInsteadOfOverflowing() {
        QuestionQuery query = of(Integer.MAX_VALUE, 20);

        assertThat(query.page()).isEqualTo(QuestionQuery.MAX_PAGE);
        assertThat(query.offset()).isEqualTo((long) (QuestionQuery.MAX_PAGE - 1) * 20);
    }

    /** Clamp phải đủ rộng để không giới hạn dữ liệu thật, nhưng vẫn không tràn `int`. */
    @Test
    void maxPageOffsetStaysWithinIntRange() {
        long maxOffset = of(QuestionQuery.MAX_PAGE, QuestionQuery.MAX_SIZE).offset();

        assertThat(maxOffset).isPositive();
        assertThat(maxOffset).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void offsetIs64BitForEveryValidPage() {
        assertThat(of(1, 20).offset()).isZero();
        assertThat(of(2, 20).offset()).isEqualTo(20L);
        assertThat(of(3, 100).offset()).isEqualTo(200L);
    }

    @Test
    void blankTagBecomesNull() {
        assertThat(new QuestionQuery(null, null, "  ", null, 1, 20).tag()).isNull();
        assertThat(new QuestionQuery(null, null, "jvm", null, 1, 20).tag()).isEqualTo("jvm");
    }

    @Test
    void blankQueryBecomesNull() {
        assertThat(new QuestionQuery(null, null, null, "   ", 1, 20).q()).isNull();
        assertThat(new QuestionQuery(null, null, null, "heap", 1, 20).hasFullText()).isTrue();
    }

    /** `withQuery` phải giữ nguyên các filter và phân trang, chỉ thay `q`. */
    @Test
    void withQueryReplacesOnlyTheSearchText() {
        QuestionQuery original = new QuestionQuery(null, Difficulty.SENIOR, "jvm", null, 4, 50);

        QuestionQuery updated = original.withQuery("dong bo");

        assertThat(updated.q()).isEqualTo("dong bo");
        assertThat(updated.difficulty()).isEqualTo(Difficulty.SENIOR);
        assertThat(updated.tag()).isEqualTo("jvm");
        assertThat(updated.page()).isEqualTo(4);
        assertThat(updated.size()).isEqualTo(50);
    }
}
