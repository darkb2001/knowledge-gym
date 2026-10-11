package com.knowledgegym.infrastructure.content;

import org.jsoup.Jsoup;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Allowlist cho SVG diagram nhúng thẳng trong `answer_html` / blog post.
 *
 * <p>Archify vẽ diagram thành inline {@code <svg>}. Trước đây cả hai sanitizer (đường import
 * docs/ và đường admin CRUD) đều không có {@code svg} trong whitelist, nên toàn bộ figure bị
 * xoá và diagram hiển thị thành khung rỗng trên FE. Hai đường ghi dùng chung hằng số này để
 * không lệch whitelist (bug cũ: sửa một bên, bên kia vẫn strip).
 *
 * <p>Tag của SVG không phân biệt hoa/thường với Jsoup, nhưng <strong>thuộc tính thì có</strong>:
 * {@code viewBox}, {@code refX}, {@code markerWidth} phải giữ đúng camelCase như tác giả viết,
 * nếu không marker mũi tên và tỉ lệ viewBox sẽ hỏng.
 *
 * <p>{@code <style>} cố ý KHÔNG nằm trong danh sách: một stylesheet nằm trong answer HTML có thể
 * restyle cả trang. Màu của diagram lấy từ CSS của reader (keyed theo {@code .kg-diagram}).
 */
public final class DiagramAllowlist {

    /** Thẻ hình học + text của diagram. */
    public static final String[] SVG_TAGS = {
            "svg", "g", "defs", "title", "desc", "rect", "circle", "ellipse", "line",
            "polyline", "polygon", "path", "text", "tspan", "marker", "pattern",
    };

    /**
     * {@code style} nằm trong {@link #SVG_ATTRS} để Jsoup giữ lại, rồi
     * {@code DiagramAllowlist.stripNonStepStyles} gỡ nó khỏi mọi element trừ vài tag hình học
     * mang {@code --step:N}. Đặt {@code style="position:fixed"} lên một {@code <div>} là vector
     * deface, nên nó không được sống sót.
     */
    public static final String[] SVG_ATTRS = {
            "class", "id", "role", "lang", "tabindex", "style",
            "aria-hidden", "aria-label", "aria-labelledby", "aria-pressed",
            "viewBox", "transform", "fill", "stroke", "stroke-width", "stroke-linecap",
            "stroke-linejoin", "stroke-dasharray", "marker-end", "pointer-events",
            "x", "y", "width", "height", "rx", "ry", "cx", "cy", "r", "x1", "y1", "x2", "y2",
            "d", "points", "font-size", "font-weight", "text-anchor", "writing-mode",
            "markerWidth", "markerHeight", "refX", "refY", "orient", "patternUnits",
            "data-preset", "data-quality-profile", "data-reader-fit", "data-reader-min-text",
            "data-reader-primary-text", "data-sequence-column-fit", "data-layout-contract",
            "data-detail", "data-detail-anchor", "data-node-label", "data-node-id",
            "data-node-kind", "data-node-context", "data-node-sublabel", "data-node-tag",
            "data-edge-from", "data-edge-to", "data-edge-key", "data-edge-id",
            "data-edge-label", "data-edge-role", "data-graph-role",
            "data-composition-points", "data-composition-crossover",
            "data-composition-independent", "data-composition-routing",
            "data-composition-frame-kind", "data-composition-frame-id",
            "data-composition-frame-label", "data-composition-edge-from",
            "data-composition-edge-to", "data-composition-edge-id",
            "data-legend", "data-legend-kind", "data-legend-label", "data-legend-x",
            "data-legend-width", "data-legend-baseline", "data-legend-semantic-kind",
            "data-legend-bridge", "data-segment-id", "data-lifecycle-initial-marker",
            "data-boundary-label",
    };

    /**
     * Tag SVG duy nhất được mang {@code style="--step:N"}. Archify dùng nó để lệch nhịp
     * animation khi bài bật ambient motion, nên chỉ cần đúng những tag hình học này.
     */
    private static final Set<String> SVG_STYLE_TAGS =
            Set.of("g", "rect", "circle", "ellipse", "line", "polygon", "path");

    /** {@code --step:N}, không kèm khai báo nào khác. */
    private static final Pattern STEP_STYLE = Pattern.compile("^\\s*--step\\s*:\\s*\\d+\\s*$");

    /**
     * Gỡ {@code style} khỏi mọi element trừ các tag hình học SVG mang đúng {@code --step:N}.
     *
     * <p>Phải chạy <em>sau</em> {@code Jsoup.clean}: allowlist giữ {@code style} để marker
     * {@code --step} sống sót, còn bước này mới thu hẹp nó về đúng cấu trúc diagram dùng.
     * Không có bước này thì {@code <div style="position:fixed;inset:0">} vẫn qua được.
     */
    public static String stripNonStepStyles(String html) {
        if (html == null || html.isBlank()) {
            return html == null ? "" : html;
        }
        var document = Jsoup.parseBodyFragment(html);
        var body = document.body();
        for (var element : body.select("[style]")) {
            var tag = element.tagName().toLowerCase(Locale.ROOT);
            var value = element.attr("style");
            if (SVG_STYLE_TAGS.contains(tag) && STEP_STYLE.matcher(value).matches()) {
                continue;
            }
            element.removeAttr("style");
        }
        // Giữ nguyên cách serialize của caller (answer_html dùng prettyPrint(false)); để mặc
        // định true sẽ chèn newline/indent, làm phình HTML và lệch ngân sách ký tự.
        document.outputSettings().prettyPrint(false);
        return body.html();
    }

    private DiagramAllowlist() {
    }
}
