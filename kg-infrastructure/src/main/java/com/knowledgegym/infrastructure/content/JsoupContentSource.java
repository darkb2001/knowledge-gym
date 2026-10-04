package com.knowledgegym.infrastructure.content;

import com.knowledgegym.content.application.ContentImportException;
import com.knowledgegym.content.application.ModuleDifficultyDefaults;
import com.knowledgegym.content.application.SearchText;
import com.knowledgegym.content.domain.model.ContentCatalog;
import com.knowledgegym.content.domain.model.ContentTrack;
import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.ParsedQuestion;
import com.knowledgegym.content.domain.model.Topic;
import com.knowledgegym.content.domain.port.AnswerHtmlSanitizer;
import com.knowledgegym.content.domain.port.ContentSource;
import com.knowledgegym.infrastructure.config.AppContentProperties;
import com.knowledgegym.shared.domain.model.Difficulty;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Đọc docs/*.html → {@link ContentCatalog}.
 *
 * Cấu trúc HTML đã kiểm trên 16 file thật:
 * <pre>
 *   div.module-header p.module-subtitle      → module description
 *   section.section[id][h2.section-title]    → nguồn tag
 *   article.qa-card
 *     h3.qa-question span.badge + text       → title (badge = Q1/YT3/💡, không phải độ khó)
 *     div.qa-answer                          → answer HTML (đã strip speak-notes/flow-diagram)
 *   div.speak-notes                          → ghi chú nói miệng, KHÔNG phải đáp án → loại
 *   div.flow-diagram                         → sơ đồ bước (flow-node/flow-arrow) → GIỮ; text
 *                                              nằm trong div, không phải &lt;p&gt; — strip sẽ mất kiến thức
 * </pre>
 *
 * Card thiếu `h3.qa-question` (có thật: 1 card ở `14-study-plan.html`) → fallback title = section title.
 * Parser không được ném lỗi vì 1 card lạ; chỉ bỏ qua khi cả title lẫn section đều rỗng.
 */
@Component
public class JsoupContentSource implements ContentSource {

    private static final Logger log = LoggerFactory.getLogger(JsoupContentSource.class);

    private static final Pattern MODULE_FILE = Pattern.compile("^(\\d{2})-([a-z0-9-]+)\\.html$");
    private static final Pattern LEADING_INDEX = Pattern.compile("^(\\d{2,3})\\s");
    private static final int MAX_ANSWER_CHARS = 20_000;

    private final int parallelism;
    private final AnswerHtmlSanitizer sanitizer;

    public JsoupContentSource(AppContentProperties.Content properties, AnswerHtmlSanitizer sanitizer) {
        this.parallelism = Math.max(1, properties.parseParallelism());
        this.sanitizer = sanitizer;
    }

    @Override
    public ContentCatalog readCatalog(String docsPath) {
        Path dir = Path.of(docsPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new ContentImportException("Thư mục docs không tồn tại: " + dir);
        }

        List<Path> moduleFiles = moduleFiles(dir);
        if (moduleFiles.isEmpty()) {
            throw new ContentImportException("Không tìm thấy file module (NN-slug.html) trong " + dir);
        }

        Index index = parseIndex(dir.resolve("index.html"));
        List<ParsedQuestion> questions = parseModules(moduleFiles, index);

        List<Topic> topics = index.topics();
        List<ModuleRef> modules = index.modules();
        log.info("Content parse xong: {} topics, {} modules, {} questions ({} file)",
                topics.size(), modules.size(), questions.size(), moduleFiles.size());
        return new ContentCatalog(index.tracks(), topics, modules, questions);
    }

    private List<Path> moduleFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> MODULE_FILE.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new ContentImportException("Không đọc được thư mục docs: " + dir, e);
        }
    }

    // ---------------------------------------------------------------- index.html

    private record Index(List<ContentTrack> tracks, List<Topic> topics, List<ModuleRef> modules,
                         Map<String, String> moduleTopicBySlug) {}

    private Index parseIndex(Path indexFile) {
        if (!Files.isRegularFile(indexFile)) {
            throw new ContentImportException("Thiếu index.html trong docs/ — cần để seed topics/modules");
        }
        Document doc = parse(indexFile);

        Map<String, String> topicSlugByModuleSlug = new LinkedHashMap<>();
        Map<String, String> topicDisplayName = new LinkedHashMap<>();
        List<String> topicOrder = new ArrayList<>();
        // track khai báo trên nav-group: data-track="aws" data-track-name="AWS DVA" …
        Map<String, String> trackOfTopic = new LinkedHashMap<>();
        Map<String, ContentTrack> tracksBySlug = new LinkedHashMap<>();

        // sidebar: div.nav-group > div.nav-label + a.nav-item[href]
        for (Element group : doc.select("nav.sidebar div.nav-group")) {
            Element label = group.selectFirst("div.nav-label");
            if (label == null) {
                continue;
            }
            String topicName = label.text().trim();
            String topicSlug = slugify(topicName);
            if (topicSlug.isEmpty()) {
                continue;
            }
            boolean newTopic = !topicDisplayName.containsKey(topicSlug);
            if (newTopic) {
                topicDisplayName.put(topicSlug, topicName);
                topicOrder.add(topicSlug);
            }
            String trackSlug = slugify(group.attr("data-track"));
            if (!trackSlug.isEmpty() && newTopic) {
                trackOfTopic.put(topicSlug, trackSlug);
                tracksBySlug.computeIfAbsent(trackSlug, slug -> new ContentTrack(
                        firstNonBlank(group.attr("data-track-name"), ContentTrack.fromSlug(slug, 0).getName()),
                        slug, tracksBySlug.size() + 1,
                        blankToNull(group.attr("data-track-desc")),
                        blankToNull(group.attr("data-track-icon"))));
            }
            for (Element link : group.select("a.nav-item[href]")) {
                String slug = moduleSlugFromHref(link.attr("href"));
                if (slug != null) {
                    topicSlugByModuleSlug.putIfAbsent(slug, topicSlug);
                }
            }
        }

        List<Topic> topics = new ArrayList<>();
        for (int i = 0; i < topicOrder.size(); i++) {
            String slug = topicOrder.get(i);
            Topic topic = new Topic(topicDisplayName.get(slug), slug, i + 1);
            String track = trackOfTopic.get(slug);
            if (track != null) {
                topic.setTrackSlug(track);
            }
            topics.add(topic);
        }

        // module meta: div.module-grid a.module-card
        Map<String, String[]> moduleMeta = new LinkedHashMap<>();
        for (Element card : doc.select("div.module-grid a.module-card[href]")) {
            String slug = moduleSlugFromHref(card.attr("href"));
            if (slug == null) {
                continue;
            }
            Element h3 = card.selectFirst("h3");
            Element p = card.selectFirst("p");
            moduleMeta.put(slug, new String[]{
                    h3 != null ? h3.text().trim() : slug,
                    p != null ? p.text().trim() : null});
        }

        List<ModuleRef> modules = new ArrayList<>();
        int displayOrder = 0;
        for (Map.Entry<String, String> entry : topicSlugByModuleSlug.entrySet()) {
            String moduleSlug = entry.getKey();
            String topicSlug = entry.getValue();
            displayOrder++;
            String[] meta = moduleMeta.get(moduleSlug);
            String name = meta != null ? meta[0] : moduleSlug;
            ModuleRef module = new ModuleRef(name, moduleSlug, displayOrder);
            module.setTopicSlug(topicSlug);
            if (meta != null) {
                module.setDescription(meta[1]);
            }
            modules.add(module);
        }

        if (modules.isEmpty()) {
            throw new ContentImportException("index.html không có module nào (div.module-grid a.module-card)");
        }
        return new Index(new ArrayList<>(tracksBySlug.values()), topics, modules, topicSlugByModuleSlug);
    }

    /** `01-java-core.html` → `01-java-core`; bỏ qua link ngoài (index.html, http://...). */
    private static String moduleSlugFromHref(String href) {
        if (href == null) {
            return null;
        }
        String clean = href.trim();
        if (clean.isEmpty() || clean.startsWith("#") || clean.contains("://") || clean.contains("/")) {
            return null;
        }
        Matcher matcher = MODULE_FILE.matcher(clean);
        return matcher.matches() ? matcher.group(1) + "-" + matcher.group(2) : null;
    }

    /** `data-track-name` rỗng → lấy tên suy từ slug; null/blank khi không có attribute. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- module files

    private List<ParsedQuestion> parseModules(List<Path> moduleFiles, Index index) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(parallelism, moduleFiles.size()));
        try {
            List<CompletableFuture<List<ParsedQuestion>>> futures = moduleFiles.stream()
                    .map(file -> CompletableFuture.supplyAsync(() -> parseModule(file), pool))
                    .toList();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<ParsedQuestion> parseModule(Path file) {
        Matcher matcher = MODULE_FILE.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return List.of();
        }
        String moduleSlug = matcher.group(1) + "-" + matcher.group(2);
        Difficulty difficulty = ModuleDifficultyDefaults.forModuleSlug(moduleSlug);

        Document doc = parse(file);
        List<ParsedQuestion> questions = new ArrayList<>();
        int sortOrder = 0;

        for (Element section : doc.select("section.section")) {
            String sectionTitle = textOf(section.selectFirst("h2.section-title"));
            Set<String> sectionTags = sectionTags(section, sectionTitle);

            for (Element card : section.select("article.qa-card")) {
                sortOrder++;
                Element questionHeading = card.selectFirst("h3.qa-question");
                String title = questionHeading != null ? questionTitle(questionHeading) : sectionTitle;
                if (title == null || title.isBlank()) {
                    log.debug("Bỏ card không có title ở {} (sortOrder={})", moduleSlug, sortOrder);
                    continue;
                }
                String answerHtml = sanitizeAnswer(card, questionHeading, title);
                if (answerHtml.isBlank()) {
                    log.debug("Bỏ card không có nội dung trả lời ở {} (sortOrder={}): {}", moduleSlug, sortOrder, title);
                    continue;
                }

                List<String> tags = new ArrayList<>(new LinkedHashSet<>(sectionTags));
                String searchable = SearchText.build(title, SearchText.stripHtml(answerHtml),
                        String.join(" ", tags));

                questions.add(new ParsedQuestion(moduleSlug, title, answerHtml, List.copyOf(tags),
                        SearchText.tokenList(searchable), sortOrder, difficulty));
            }
        }

        // Card nằm ngoài <section> (không có ở 16 file hiện tại, nhưng HTML có thể đổi).
        int outsideCards = doc.select("article.qa-card").size() - doc.select("section.section article.qa-card").size();
        if (outsideCards > 0) {
            log.warn("{} có {} article.qa-card nằm ngoài section.section — đã bỏ qua", moduleSlug, outsideCards);
        }
        return questions;
    }

    private Set<String> sectionTags(Element section, String sectionTitle) {
        Set<String> tags = new LinkedHashSet<>();
        String id = section.id();
        if (!id.isBlank()) {
            tags.add(id);
        }
        String titleSlug = slugify(sectionTitle);
        if (!titleSlug.isBlank()) {
            tags.add(titleSlug);
        }
        return tags;
    }

    /** `&lt;span class="badge"&gt;Q1&lt;/span&gt; Phân biệt ...` → `Phân biệt ...` (badge không phải độ khó). */
    private static String questionTitle(Element heading) {
        Element copy = heading.clone();
        copy.select("span.badge").remove();
        return copy.text().trim();
    }

    private String sanitizeAnswer(Element card, Element questionHeading, String title) {
        Element answer = card.selectFirst("div.qa-answer");
        Element source = answer != null ? answer : card;
        Element copy = source.clone();
        if (questionHeading != null) {
            copy.select("h3.qa-question").remove();
        }
        // Strip speak-notes/flow-diagram/script + whitelist do sanitizer lo — dùng chung với
        // đường ghi của admin (ManageQuestionsUseCase) để 2 nguồn ghi không lệch whitelist.
        String cleaned = sanitizer.sanitize(copy.html());

        if (cleaned.length() > MAX_ANSWER_CHARS) {
            log.warn("Answer quá dài ({} chars) — cắt bớt giữ HTML hợp lệ: {}", cleaned.length(), title);
            cleaned = truncateWellFormedHtml(cleaned, MAX_ANSWER_CHARS);
        }
        return cleaned;
    }

    /**
     * Cắt HTML mà không xẻ giữa thẻ: bỏ node cuối (DOM) đến khi dưới ngân sách; nếu còn 1 node
     * quá lớn thì thay bằng text đã cắt (mất markup của node đó nhưng output vẫn parse được).
     */
    static String truncateWellFormedHtml(String html, int maxChars) {
        if (html == null || html.length() <= maxChars) {
            return html == null ? "" : html;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        Element body = doc.body();
        while (body.html().length() > maxChars && body.childNodeSize() > 1) {
            body.childNode(body.childNodeSize() - 1).remove();
        }
        if (body.html().length() <= maxChars) {
            return body.html();
        }
        String text = body.text();
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars);
        }
        body.empty();
        body.appendText(text);
        return body.html();
    }

    private static Document parse(Path file) {
        try {
            return Jsoup.parse(file.toFile(), "UTF-8");
        } catch (IOException e) {
            throw new ContentImportException("Không parse được " + file.getFileName(), e);
        }
    }

    private static String textOf(Element element) {
        return element == null ? "" : element.text().trim();
    }

    /** Bỏ tiền tố số thứ tự (`01 Java Core` → `java-core`) và dấu tiếng Việt. */
    static String slugify(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = LEADING_INDEX.matcher(raw.trim()).replaceFirst("");
        return SearchText.stripDiacritics(cleaned)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /** Giữ lại cho test — số card `.qa-card` thô của 1 file. */
    static int rawCardCount(Path file) {
        return parse(file).select("article.qa-card").size();
    }
}
