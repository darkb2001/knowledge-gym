package com.knowledgegym.infrastructure.content;

import com.knowledgegym.content.domain.port.AnswerHtmlSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Sanitize `answer_html` bằng Jsoup {@link Safelist} — dùng chung cho **mọi** đường ghi
 * (import docs/ và admin CRUD). Xem {@link AnswerHtmlSanitizer} để biết lý do port này tồn tại.
 *
 * Bỏ trước khi clean:
 * - `script`/`style`/`iframe`/`form` — vector XSS, không phải nội dung học thuật.
 * - `speak-notes` — ghi chú nói miệng (docs/ có ~247 block), không phải đáp án.
 *
 * <strong>Không</strong> strip `flow-diagram`: 86 block trong docs/ đều chứa text thuật toán
 * (flow-node / flow-arrow), không phải trang trí. Đo thật 2026-09-30: strip mất ~105k ký tự
 * kiến thức (vd WAL/undo-log từng bước). Giữ markup + class; FE style thành step-flow.
 *
 * <p>Diagram Archify là inline {@code <svg>} (xem {@link DiagramAllowlist}), không phải
 * {@code flow-diagram} dạng div, nên cả hai đều phải nằm trong whitelist — nếu không figure
 * sẽ bị xoá sạch và FE render ra khung rỗng.
 *
 * Thuộc tính `on*` và URL `javascript:` bị loại bởi {@link Safelist} (không nằm trong whitelist).
 */
@Component
public class JsoupAnswerHtmlSanitizer implements AnswerHtmlSanitizer {

    /**
     * Whitelist giữ đúng các class mà FE cần để style
     * (code-block, feature-table, callout, ans-block, flow-diagram, tok-*) **và** SVG diagram
     * (xem {@link DiagramAllowlist}). Chặn script/iframe/on* và mọi thứ không có trong danh sách.
     */
    private static final Safelist SAFELIST = new Safelist()
            .addTags("p", "br", "hr", "strong", "b", "em", "i", "u", "s", "code", "pre",
                    "ul", "ol", "li", "blockquote", "small", "sup", "sub",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "dl", "dt", "dd", "span", "div", "figure", "figcaption", "a")
            .addTags(DiagramAllowlist.SVG_TAGS)
            .addAttributes("span", "class")
            .addAttributes("div", "class")
            .addAttributes("p", "class")
            .addAttributes("table", "class")
            .addAttributes("th", "class", "colspan", "rowspan")
            .addAttributes("td", "class", "colspan", "rowspan")
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            .addAttributes("figure", "class", "id")
            .addAttributes(":all", DiagramAllowlist.SVG_ATTRS)
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "http", "https")
            // Link tương đối (15-auth-...html) sẽ hỏng trong SPA → bỏ href, giữ text.
            .preserveRelativeLinks(false);

    /** Chỉ strip phần không phải đáp án / XSS — không gồm flow-diagram. */
    private static final String STRIP_SELECTOR =
            "div.speak-notes, script, style, iframe, form";

    @Override
    public String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        var body = Jsoup.parseBodyFragment(rawHtml).body();
        body.select(STRIP_SELECTOR).remove();
        String cleaned = Jsoup.clean(body.html(), "", SAFELIST,
                new Document.OutputSettings().prettyPrint(false)).trim();
        // Allowlist giữ `style` để marker `--step` sống sót; bước này mới thu hẹp nó về đúng
        // cấu trúc diagram dùng, chặn style deface kiểu position:fixed trên <div>/<p>.
        return DiagramAllowlist.stripNonStepStyles(cleaned);
    }
}
