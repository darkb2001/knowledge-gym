package com.knowledgegym.infrastructure.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hồi quy: sanitize phải strip XSS + speak-notes, nhưng **không** được vứt flow-diagram.
 * Đo thật 2026-09-30 trên docs/: strip flow-diagram mất ~105k ký tự kiến thức.
 */
class JsoupAnswerHtmlSanitizerTest {

    private final JsoupAnswerHtmlSanitizer sanitizer = new JsoupAnswerHtmlSanitizer();

    @Test
    void stripsSpeakNotesButKeepsFlowDiagramNodes() {
        String html = """
                <p>Young và Old generation.</p>
                <div class="speak-notes"><ul><li>ghi chú nói miệng</li></ul></div>
                <div class="flow-diagram">
                  <div class="flow-row">
                    <div class="flow-node primary">Eden</div>
                    <div class="flow-node secondary">Survivor</div>
                    <div class="flow-node success">Old</div>
                  </div>
                  <div class="flow-arrow">↓ Minor GC</div>
                </div>
                """;

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned).doesNotContain("speak-notes", "ghi chú nói miệng");
        assertThat(cleaned).contains("flow-diagram", "flow-node", "flow-arrow");
        assertThat(cleaned).contains("Eden", "Survivor", "Old", "Minor GC");
        assertThat(cleaned).contains("Young và Old generation");
    }

    @Test
    void stripsScriptIframeAndEventHandlers() {
        String cleaned = sanitizer.sanitize("""
                <p onclick="alert(1)">ok</p>
                <script>alert('xss')</script>
                <iframe src="https://evil.example"></iframe>
                <p>sống</p>
                """);

        assertThat(cleaned).doesNotContain("<script", "alert(", "<iframe", "onclick");
        assertThat(cleaned).contains("sống");
    }

    @Test
    void keepsLearningStructureClasses() {
        String cleaned = sanitizer.sanitize("""
                <div class="ans-block"><div class="ans-label">What</div><p>JVM</p></div>
                <div class="callout callout-info"><div class="callout-title">Tip</div><p>x</p></div>
                <table class="feature-table"><tr><td>a</td></tr></table>
                <div class="code-block"><pre><code><span class="tok-keyword">class</span> A {}</code></pre></div>
                """);

        assertThat(cleaned).contains("ans-block", "ans-label", "callout", "callout-title",
                "feature-table", "code-block", "tok-keyword");
    }

    /**
     * Hồi quy: diagram Archify là inline SVG. Khi whitelist thiếu {@code <svg>}, Jsoup xoá cả
     * figure và FE render ra khung rỗng — đo thật 2026-10-11: figure 17.907 ký tự còn 40 ký tự.
     */
    @Test
    void keepsInlineSvgDiagramAndItsLineBreakComments() {
        String html = """
                <figure class="kg-diagram" id="kg-outbox" data-preset="classic">
                  <svg viewBox="0 0 1040 660" role="img" aria-labelledby="t">
                    <title id="t">Outbox</title>
                    <defs><marker markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                      <polygon points="0 0, 10 3.5, 0 7" class="m-default"/>
                    </marker></defs>
                    <rect x="1" y="2" width="40" height="20" rx="4" class="c-backend"/>
                    <path d="M0 0 L10 10" marker-end="url(#m)" stroke-width="1.4"/>
                    <text x="5" y="30" font-size="12" data-node-label="Outbox">Outbox</text>
                    <g style="--step:7" data-node-id="n1"><circle cx="4" cy="4" r="2"/></g>
                  </svg>
                  <figcaption>Outbox pattern</figcaption>
                </figure>
                """;

        String cleaned = sanitizer.sanitize(html);

        assertThat(cleaned).contains("<svg", "viewBox=\"0 0 1040 660\"", "kg-diagram");
        // camelCase SVG geometry attributes survive verbatim.
        assertThat(cleaned).contains("markerWidth", "refX", "refY", "marker-end");
        assertThat(cleaned).contains("c-backend", "data-node-label", "--step:7");
        assertThat(cleaned).contains("Outbox pattern");
    }

    @Test
    void stripsSvgOnlyScriptPayloads() {
        String cleaned = sanitizer.sanitize("""
                <svg viewBox="0 0 10 10">
                  <script>alert('xss')</script>
                  <rect x="1" y="1" width="4" height="4" onclick="alert(1)"/>
                  <a href="javascript:alert(1)"><text x="1" y="1">click</text></a>
                </svg>
                """);

        assertThat(cleaned).doesNotContain("<script", "alert(", "onclick", "javascript:");
        assertThat(cleaned).contains("<svg", "<rect");
    }

    /**
     * `style` chỉ được sống trên tag hình học SVG với đúng `--step:N`. Nếu thu hẹp này hỏng,
     * `<div style="position:fixed;inset:0">` quay lại thành vector deface toàn trang.
     */
    @Test
    void keepsStepDelayOnSvgShapesButStripsEveryOtherStyle() {
        String cleaned = sanitizer.sanitize("""
                <div class="ans-block" style="position:fixed;inset:0;z-index:9999">
                  <p style="color:red">deface</p>
                  <svg viewBox="0 0 10 10">
                    <rect x="1" y="1" width="4" height="4" style="--step:2"/>
                    <circle cx="1" cy="1" r="1" style="--step:0"/>
                    <text x="1" y="1" style="--step:5">T</text>
                  </svg>
                </div>
                """);

        assertThat(cleaned).contains("--step:2", "--step:0");
        assertThat(cleaned).doesNotContain("position:fixed", "color:red", "z-index", "inset:0");
        // `text` is not a geometry tag, so its style goes too.
        assertThat(cleaned).doesNotContain("--step:5");
    }
}
