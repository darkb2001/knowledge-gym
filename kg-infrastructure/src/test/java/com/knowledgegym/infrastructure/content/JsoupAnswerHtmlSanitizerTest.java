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
}
