package com.knowledgegym.infrastructure.blog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsoupGeneratedBlogSanitizerTest {
    private final JsoupHtmlSanitizer sanitizer=new JsoupHtmlSanitizer();

    @Test void generatedContentCannotMintCitationLinksOrSourceList(){
        String safe=sanitizer.sanitizeGenerated("<h2>Body</h2><p>Useful content</p><h2>Sources</h2><ul><li><a href='https://fake.example'>Invented citation</a></li></ul><a href='javascript:alert(1)'>unsafe</a><script>alert(1)</script>");
        assertTrue(safe.contains("Useful content"));
        assertFalse(safe.contains("Invented citation"));
        assertFalse(safe.contains("href="));
        assertFalse(safe.contains("<script"));
    }

    @Test void applicationCitationsKeepRelAttributes(){
        String safe=sanitizer.sanitize("<h2>Sources</h2><ul><li><a href=\"https://example.org/a\" rel=\"nofollow noopener\">Grounded</a></li></ul>");
        assertTrue(safe.contains("href=\"https://example.org/a\""));
        assertTrue(safe.contains("rel=\"nofollow noopener\""));
        assertTrue(safe.contains("Grounded"));
    }
}
