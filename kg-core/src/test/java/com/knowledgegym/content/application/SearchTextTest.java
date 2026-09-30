package com.knowledgegym.content.application;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchText là ranh giới giữa "câu hỏi viết bằng thuật ngữ Anh/Việt" và "người học gõ tiếng Việt".
 * Test kiểm đúng 4 cơ chế: bỏ dấu, n-gram, tách slug, song ngữ — cộng thêm giới hạn kích thước.
 */
class SearchTextTest {

    private static Set<String> tokens(String text) {
        String built = SearchText.build(text, null, null);
        return built.isBlank() ? Set.of() : Set.of(built.split(" "));
    }

    @Test
    void diacriticVariantsBothIndexed() {
        Set<String> result = tokens("Bất đồng bộ trong Java");

        assertThat(result).contains("bat-dong-bo").as("n-gram không dấu của cụm tiếng Việt");
        assertThat(result).contains("java");
    }

    @Test
    void vietnamesePhraseMapsToEnglishTerm() {
        // "sao" không được là stopword, nếu không "sao lưu" (backup) sẽ mất khỏi index.
        Set<String> result = tokens("Chiến lược sao lưu dữ liệu");

        assertThat(result).contains("sao-luu", "backup", "replication", "replica");
    }

    @Test
    void englishTermMapsBackToVietnameseSynonym() {
        Set<String> result = tokens("Explain PostgreSQL replication");

        assertThat(result).contains("sao-luu", "backup", "replication");
    }

    @Test
    void tagsSplitIntoPiecesSoShortQueryMatches() {
        Set<String> result = Set.of(SearchText
                .build("Không có title tiếng Việt", null, "jvm-basics garbage-collection")
                .split(" "));

        assertThat(result).contains("jvm", "basics", "garbage", "collection");
    }

    @Test
    void abbreviationsAreIndexed() {
        Set<String> result = tokens("Garbage Collection và GC tuning");

        assertThat(result).contains("gc", "garbage", "collection");
    }

    @Test
    void stopwordsAreDropped() {
        Set<String> result = tokens("What is the difference between List and Set?");

        assertThat(result).doesNotContain("the", "is", "and", "between", "what");
        assertThat(result).contains("difference", "list", "set");
        // N-gram chỉ sinh từ cụm nội dung, không sinh từ cụm hư từ.
        assertThat(result).contains("difference-list", "difference-list-set");
        assertThat(result).doesNotContain("what-is", "is-the", "the-difference");
    }

    @Test
    void htmlIsStrippedBeforeTokenizing() {
        String text = SearchText.stripHtml("<p>Dùng <code>CompletableFuture</code> nhé</p>");

        assertThat(text).doesNotContain("<").contains("CompletableFuture");
    }

    @Test
    void answerContributesTokensEvenWithoutNgrams() {
        Set<String> result = Set.of(SearchText
                .build("Tiêu đề ngắn", "Concurrency utilities giải pháp", null)
                .split(" "));

        assertThat(result).contains("concurrency", "utilities", "giai");
        // N-gram chỉ sinh từ title/tag — answer dài nên không sinh cụm.
        assertThat(result).doesNotContain("concurrency-utilities");
    }

    /**
     * Nhóm synonym có thể chỉ được kích hoạt bởi từ trong **answer** (`thu-hoi-rac` chỉ xuất hiện
     * khi gặp `gc`/`garbage` trong câu trả lời). Answer lại phải vào index **sau** n-gram để không
     * chiếm hết budget — hai yêu cầu này xung đột nếu code chỉ collect answer một lần.
     */
    @Test
    void synonymsTriggeredOnlyFromAnswerAreStillExpanded() {
        Set<String> result = Set.of(SearchText
                .build("Tiêu đề ngắn", "Cơ chế garbage collection dọn vùng nhớ", null)
                .split(" "));

        assertThat(result).contains("garbage", "gc", "rac", "thu-hoi-rac");
    }

    /** Title/tag phải thắng khi answer dài: answer không được chiếm chỗ của n-gram. */
    @Test
    void longAnswerDoesNotCrowdOutTitleNgrams() {
        StringBuilder longAnswer = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longAnswer.append("filler").append(i).append(' ');
        }
        String result = SearchText.build("Bất đồng bộ trong Java", longAnswer.toString(), "async");

        assertThat(result.split(" ")).contains("bat-dong-bo", "dong-bo", "async");
    }

    @Test
    void outputIsBoundedAndDeduplicated() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            big.append("token").append(i % 50).append(' ');
        }
        String[] result = SearchText.build(big.toString(), null, null).split(" ");

        assertThat(result).hasSizeLessThanOrEqualTo(140);
        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    void tokenListRoundTrips() {
        assertThat(SearchText.tokenList("  a b   a c ")).containsExactly("a", "b", "c");
        assertThat(SearchText.tokenList(null)).isEmpty();
        assertThat(SearchText.tokenList("  ")).isEmpty();
    }

    // ---------------------------------------------------------------- normalizeQuery

    /**
     * Hồi quy BLOCKER: index giữ `không` (bỏ dấu `khong` nằm trong stopword nên bị loại), nên
     * query phải bị chuẩn hoá y hệt. Trước fix, `plainto_tsquery('simple','khong dong bo')` sinh
     * `'khong' & 'dong' & 'bo'` — `khong` không tồn tại trong index → 0 hit.
     */
    @Test
    void normalizeQueryDropsVietnameseStopwordsAndDiacritics() {
        assertThat(SearchText.normalizeQuery("không đồng bộ")).isEqualTo("dong bo");
        assertThat(SearchText.normalizeQuery("đồng bộ")).isEqualTo("dong bo");
    }

    @Test
    void normalizeQueryDropsEnglishStopwords() {
        assertThat(SearchText.normalizeQuery("what is the difference between List and Set"))
                .isEqualTo("difference list set");
    }

    /** Query toàn hư từ không còn token nội dung → phải trả rỗng, không phải chuỗi hư từ. */
    @Test
    void normalizeQueryOfPureStopwordsIsEmpty() {
        assertThat(SearchText.normalizeQuery("là gì")).isEmpty();
        assertThat(SearchText.normalizeQuery("what is it")).isEmpty();
        assertThat(SearchText.normalizeQuery(null)).isEmpty();
        assertThat(SearchText.normalizeQuery("   ")).isEmpty();
    }

    /** Từ nội dung vẫn phải sống sót, kèm dạng bỏ dấu khi từ gốc có dấu. */
    @Test
    void normalizeQueryKeepsContentTerms() {
        assertThat(SearchText.normalizeQuery("sao lưu")).isEqualTo("sao luu");
        assertThat(SearchText.normalizeQuery("Heap")).isEqualTo("heap");
        assertThat(SearchText.normalizeQuery("N+1")).isEqualTo("n+1");
    }

    /** Query đã chuẩn hoá phải khớp đúng token mà index đã sinh cho cùng cụm. */
    @Test
    void normalizedQueryTokensExistInBuiltIndex() {
        String indexed = SearchText.build("Bất đồng bộ trong Java", null, null);
        Set<String> indexTokens = Set.of(indexed.split(" "));

        for (String token : SearchText.normalizeQuery("bất đồng bộ").split(" ")) {
            assertThat(indexTokens).as("token '%s' phải có trong index", token).contains(token);
        }
    }
}
