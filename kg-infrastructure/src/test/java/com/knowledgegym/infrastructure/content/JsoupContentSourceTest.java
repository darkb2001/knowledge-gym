package com.knowledgegym.infrastructure.content;

import com.knowledgegym.content.application.ContentImportException;
import com.knowledgegym.content.domain.model.ContentCatalog;
import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.ParsedQuestion;
import com.knowledgegym.infrastructure.config.AppContentProperties;
import com.knowledgegym.shared.domain.model.Difficulty;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser contract — chạy trên fixture `docs-mini/` (4 file, 12 card) copy cấu trúc từ docs/ thật,
 * kèm các ca khó: card thiếu badge, card thiếu `h3.qa-question`, `<script>` XSS, `iframe`, `onclick`,
 * `speak-notes`, `flow-diagram`.
 */
class JsoupContentSourceTest {

    private final JsoupContentSource source =
            new JsoupContentSource(new AppContentProperties.Content("", 4), new JsoupAnswerHtmlSanitizer());

    private static Path fixtures() {
        URL url = JsoupContentSourceTest.class.getResource("/fixtures/docs-mini");
        assertThat(url).as("fixture docs-mini phải tồn tại").isNotNull();
        return Path.of(url.getPath());
    }

    @Test
    void parsesTopicsModulesAndQuestions() {
        ContentCatalog catalog = source.readCatalog(fixtures().toString());

        assertThat(catalog.topics()).extracting("slug")
                .containsExactly("foundation", "backend", "roadmap");
        assertThat(catalog.topics()).extracting("displayOrder").containsExactly(1, 2, 3);

        assertThat(catalog.modules()).hasSize(4);
        Map<String, String> topicByModule = catalog.modules().stream()
                .collect(Collectors.toMap(ModuleRef::getSlug, ModuleRef::getTopicSlug));
        assertThat(topicByModule)
                .containsEntry("01-java-core", "foundation")
                .containsEntry("02-multithreading", "foundation")
                .containsEntry("03-spring-boot", "backend")
                .containsEntry("15-auth-rbac-oauth", "roadmap");

        // 6 (java-core) + 2 (multithreading) + 3 (spring-boot) + 2 (auth)
        assertThat(catalog.questionCount()).isEqualTo(13);
    }

    @Test
    void topicNameComesFromNavLabelAndModuleNameFromCard() {
        ContentCatalog catalog = source.readCatalog(fixtures().toString());

        assertThat(catalog.topics()).extracting("name")
                .containsExactly("Foundation", "Backend", "Roadmap");

        ModuleRef javaCore = catalog.modules().stream()
                .filter(module -> module.getSlug().equals("01-java-core"))
                .findFirst().orElseThrow();
        assertThat(javaCore.getName()).isEqualTo("Java Core & Memory");
        assertThat(javaCore.getDescription()).isEqualTo("JVM, GC, Collections, String Pool");
        assertThat(javaCore.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void stripsBadgeFromTitleButKeepsCardWithoutBadge() {
        List<ParsedQuestion> javaCore = questionsOf(source.readCatalog(fixtures().toString()), "01-java-core");

        assertThat(javaCore).hasSize(6);
        assertThat(javaCore.get(0).title()).isEqualTo("Phân biệt JDK, JRE và JVM?");
        assertThat(javaCore.get(1).title()).isEqualTo("Bộ nhớ Heap chia thành các vùng nào?");
    }

    @Test
    void fallsBackToSectionTitleWhenCardHasNoQuestionHeading() {
        List<ParsedQuestion> javaCore = questionsOf(source.readCatalog(fixtures().toString()), "01-java-core");

        assertThat(javaCore.get(3).title()).isEqualTo("Collections Framework");
        assertThat(javaCore.get(3).answerHtml()).contains("title fallback về section title");
    }

    @Test
    void sortOrderIsSequentialPerModule() {
        ContentCatalog catalog = source.readCatalog(fixtures().toString());

        assertThat(questionsOf(catalog, "01-java-core")).extracting(ParsedQuestion::sortOrder)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(questionsOf(catalog, "02-multithreading")).extracting(ParsedQuestion::sortOrder)
                .containsExactly(1, 2);
        assertThat(questionsOf(catalog, "15-auth-rbac-oauth")).extracting(ParsedQuestion::sortOrder)
                .containsExactly(1, 2);
    }

    @Test
    void removesSpeakNotesButKeepsFlowDiagramContent() {
        List<ParsedQuestion> javaCore = questionsOf(source.readCatalog(fixtures().toString()), "01-java-core");

        String jdkAnswer = javaCore.get(0).answerHtml();
        assertThat(jdkAnswer).doesNotContain("speak-notes", "ghi chú nói miệng");
        assertThat(jdkAnswer).contains("JDK ⊃ JRE ⊃ JVM");

        // flow-diagram chứa text thuật toán trong flow-node (không phải <p>) — phải giữ.
        // Trước fix deep review: strip mất ~105k ký tự kiến thức trên docs/ thật.
        String heapAnswer = javaCore.get(1).answerHtml();
        assertThat(heapAnswer).contains("flow-diagram", "flow-node", "Eden", "Survivor", "Old");
        assertThat(heapAnswer).contains("Full GC là stop-the-world");
    }

    @Test
    void sanitizesScriptIframeEventHandlersAndJavascriptUrls() {
        List<ParsedQuestion> javaCore = questionsOf(source.readCatalog(fixtures().toString()), "01-java-core");

        String xssAnswer = javaCore.get(4).answerHtml();
        assertThat(xssAnswer).doesNotContain("<script", "alert(");
        assertThat(xssAnswer).contains("Đoạn này phải sống sót qua sanitize");

        String linkAnswer = javaCore.get(5).answerHtml();
        assertThat(linkAnswer).doesNotContain("onclick", "<iframe", "javascript:");
    }

    @Test
    void keepsClassesNeededByFrontendAndAbsoluteLinks() {
        List<ParsedQuestion> javaCore = questionsOf(source.readCatalog(fixtures().toString()), "01-java-core");

        assertThat(javaCore.get(0).answerHtml()).contains("code-block");
        assertThat(javaCore.get(2).answerHtml()).contains("feature-table");

        // Link tuyệt đối http(s) giữ nguyên; link tương đối bị bỏ href vì FE (SPA) không serve
        // file HTML tĩnh — giữ lại sẽ tạo link hỏng. Text của <a> vẫn còn.
        String linkAnswer = javaCore.get(5).answerHtml();
        assertThat(linkAnswer).contains("https://docs.oracle.com/javase/specs/");
        assertThat(linkAnswer).doesNotContain("15-auth-rbac-oauth.html");
        assertThat(linkAnswer).contains("module Auth/RBAC");
    }

    @Test
    void derivesTagsFromSectionIdAndTitle() {
        List<ParsedQuestion> javaCore = questionsOf(source.readCatalog(fixtures().toString()), "01-java-core");

        assertThat(javaCore.get(0).tags()).containsExactlyInAnyOrder("jvm", "nen-tang-jvm-bo-nho");
        assertThat(javaCore.get(2).tags()).containsExactlyInAnyOrder("collections", "collections-framework");
    }

    @Test
    void assignsDifficultyFromModuleSlug() {
        ContentCatalog catalog = source.readCatalog(fixtures().toString());

        assertThat(questionsOf(catalog, "01-java-core")).allMatch(question -> question.difficulty() == Difficulty.MID);
        assertThat(questionsOf(catalog, "03-spring-boot")).allMatch(question -> question.difficulty() == Difficulty.MID);
    }

    @Test
    void searchKeywordsCarryVietnameseSynonymsDiacriticsAndAbbreviations() {
        ContentCatalog catalog = source.readCatalog(fixtures().toString());

        // Title có "Bộ nhớ" → n-gram bo-nho → kéo theo memory/heap/stack.
        Set<String> heapKeywords = Set.copyOf(
                questionsOf(catalog, "01-java-core").get(1).searchKeywords());
        assertThat(heapKeywords).contains("bo-nho", "memory", "heap", "stack");
        // "rác (garbage)" → kéo theo gc + thu-hoi-rac
        assertThat(heapKeywords).contains("garbage", "rac", "gc", "thu-hoi-rac");
        // tag slug tách phần
        assertThat(heapKeywords).contains("jvm");

        // Title có "bất đồng bộ" → n-gram bat-dong-bo → kéo theo async/asynchronous.
        Set<String> concurrencyKeywords = Set.copyOf(
                questionsOf(catalog, "02-multithreading").get(1).searchKeywords());
        assertThat(concurrencyKeywords)
                .contains("bat-dong-bo", "async", "asynchronous", "dong-bo", "luong", "thread");
    }

    @Test
    void failsFastWhenDocsDirectoryMissing() {
        assertThatThrownBy(() -> source.readCatalog("/khong/ton/tai"))
                .isInstanceOf(ContentImportException.class)
                .hasMessageContaining("không tồn tại");
    }

    @Test
    void slugifyHandlesVietnameseAndIndexPrefix() {
        assertThat(JsoupContentSource.slugify("Kế hoạch ôn tập")).isEqualTo("ke-hoach-on-tap");
        assertThat(JsoupContentSource.slugify("01 Java Core")).isEqualTo("java-core");
        assertThat(JsoupContentSource.slugify("Domain & Craft")).isEqualTo("domain-craft");
        assertThat(JsoupContentSource.slugify("  ")).isEmpty();
    }

    @Test
    void truncateWellFormedHtmlDoesNotSplitTags() {
        String html = "<p>một</p><p>hai</p><p>ba và thêm chữ</p>";
        String truncated = JsoupContentSource.truncateWellFormedHtml(html, 20);
        assertThat(truncated).doesNotContain("ba và");
        // Output phải parse lại được — không còn thẻ mở dở từ substring giữa tag.
        org.jsoup.nodes.Document reparsed = org.jsoup.Jsoup.parseBodyFragment(truncated);
        assertThat(reparsed.body().getAllElements()).isNotEmpty();
        assertThat(truncated.length()).isLessThanOrEqualTo(20);
    }

    private static List<ParsedQuestion> questionsOf(ContentCatalog catalog, String moduleSlug) {
        return catalog.questions().stream()
                .filter(question -> question.moduleSlug().equals(moduleSlug))
                .toList();
    }
}
