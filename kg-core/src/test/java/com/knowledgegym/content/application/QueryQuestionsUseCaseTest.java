package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.shared.domain.model.Difficulty;
import com.knowledgegym.shared.domain.model.PageResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hồi quy BLOCKER tìm kiếm tiếng Việt: use case phải chuẩn hoá `q` **trước** khi xuống repository,
 * để truy vấn dùng đúng không gian token mà index đã sinh.
 *
 * Trước fix, `q="không đồng bộ"` xuống tới Postgres nguyên văn; `plainto_tsquery('simple',
 * 'khong dong bo')` sinh `'khong' & 'dong' & 'bo'` trong khi index không có token `khong`
 * (dạng bỏ dấu của "không" nằm trong STOPWORDS) → 0 hit.
 */
class QueryQuestionsUseCaseTest {

    private final CapturingQuestionRepository repository = new CapturingQuestionRepository();
    private final QueryQuestionsUseCase useCase = new QueryQuestionsUseCase(repository);

    private QuestionQuery queryWith(String q) {
        return new QuestionQuery(null, Difficulty.MID, null, q, 1, 20);
    }

    @Test
    void vietnameseQueryIsNormalizedBeforeReachingRepository() {
        useCase.execute(queryWith("không đồng bộ"));

        assertThat(repository.captured().q()).isEqualTo("dong bo");
        assertThat(repository.captured().hasFullText()).isTrue();
    }

    @Test
    void englishStopwordsAreRemovedFromQuery() {
        useCase.execute(queryWith("what is the difference between List and Set"));

        assertThat(repository.captured().q()).isEqualTo("difference list set");
    }

    /** Query toàn hư từ trở thành "không filter" — hành vi được assert tường minh. */
    @Test
    void pureStopwordQueryBecomesNoFilter() {
        useCase.execute(queryWith("là gì"));

        assertThat(repository.captured().q()).isNull();
        assertThat(repository.captured().hasFullText()).isFalse();
    }

    @Test
    void blankQueryBecomesNoFilter() {
        useCase.execute(queryWith("   "));

        assertThat(repository.captured().q()).isNull();
    }

    /** Nội dung thật phải đi qua nguyên vẹn (đã bỏ dấu) — không được nuốt mất từ khoá. */
    @Test
    void contentTokensSurviveNormalization() {
        useCase.execute(queryWith("sao lưu"));
        assertThat(repository.captured().q()).isEqualTo("sao luu");

        useCase.execute(queryWith("N+1 query"));
        assertThat(repository.captured().q()).isEqualTo("n+1 query");
    }

    /** Chuẩn hoá chỉ đụng `q`; filter và phân trang phải nguyên vẹn xuống repository. */
    @Test
    void otherFiltersArePassedThroughUntouched() {
        UUID moduleId = UUID.randomUUID();
        useCase.execute(new QuestionQuery(moduleId, Difficulty.SENIOR, "jvm", "heap", 3, 50));

        assertThat(repository.captured().moduleId()).isEqualTo(moduleId);
        assertThat(repository.captured().difficulty()).isEqualTo(Difficulty.SENIOR);
        assertThat(repository.captured().tag()).isEqualTo("jvm");
        assertThat(repository.captured().page()).isEqualTo(3);
        assertThat(repository.captured().size()).isEqualTo(50);
        assertThat(repository.captured().q()).isEqualTo("heap");
    }

    @Test
    void adminSearchIsExplicitAndKeepsNormalizedFilters() {
        var port = org.mockito.Mockito.mock(QuestionRepository.class);
        var query = new QuestionQuery(UUID.randomUUID(), Difficulty.MID, "java", "không đồng bộ", 2, 10);
        new QueryQuestionsUseCase(port).executeAdmin(query);
        org.mockito.Mockito.verify(port).searchAdmin(query.withQuery("dong bo"));
        org.mockito.Mockito.verify(port, org.mockito.Mockito.never()).search(org.mockito.ArgumentMatchers.any());
    }

    /** Repository nhận gì, để assert trực tiếp thay vì suy đoán từ kết quả. */
    private static final class CapturingQuestionRepository implements QuestionRepository {
        private final AtomicReference<QuestionQuery> lastQuery = new AtomicReference<>();

        QuestionQuery captured() {
            return lastQuery.get();
        }

        @Override
        public PageResult<Question> search(QuestionQuery query) {
            lastQuery.set(query);
            return new PageResult<>(List.of(), query.page(), query.size(), 0);
        }

        @Override
        public Optional<Question> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<Question> findByIds(Collection<UUID> ids) {
            return List.of();
        }

        @Override
        public Optional<Question> findByModuleIdAndSortOrder(UUID moduleId, int sortOrder) {
            return Optional.empty();
        }

        @Override
        public int saveOrUpdateByNaturalKey(List<Question> questions) {
            return 0;
        }

        @Override
        public int deleteAbsentSortOrders(UUID moduleId, Collection<Integer> keepSortOrders) {
            return 0;
        }

        @Override
        public Question save(Question question) {
            return question;
        }

        @Override
        public void deleteById(UUID id) {
        }

        @Override
        public int nextSortOrder(UUID moduleId) {
            return 1;
        }
    }
}
