package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import com.knowledgegym.infrastructure.content.DiagramAllowlist;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/** Strict allowlist for readable technical articles; external links/images remain HTTPS-only. */
@Component
public class JsoupHtmlSanitizer implements BlogPostsUseCase.HtmlSanitizer {

    private static final Safelist BLOG = buildSafelist();

    private static Safelist buildSafelist() {
        Safelist safelist = Safelist.none()
                .addTags("p", "br", "hr", "strong", "b", "em", "i", "u", "s", "code", "pre",
                        "ul", "ol", "li", "blockquote", "small", "sup", "sub",
                        "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
                        "h1", "h2", "h3", "h4", "h5", "h6",
                        "dl", "dt", "dd", "span", "div", "a", "figure", "figcaption", "img")
                // Inline SVG diagrams share one allowlist with the answer_html path so fixing
                // one write path can never leave the other stripping the figure.
                .addTags(DiagramAllowlist.SVG_TAGS)
                .addAttributes("a", "href", "title", "rel")
                .addAttributes("code", "class")
                .addAttributes("pre", "class")
                .addAttributes("th", "colspan", "rowspan")
                .addAttributes("td", "colspan", "rowspan")
                .addAttributes("span", "class")
                .addAttributes("div", "class")
                .addAttributes("figure", "class", "id")
                .addAttributes("img", "src", "alt", "title", "loading")
                .addAttributes(":all", DiagramAllowlist.SVG_ATTRS)
                .addProtocols("a", "href", "https")
                .addProtocols("img", "src", "https");
        // <style> is intentionally absent: a stylesheet inside article HTML could restyle the
        // whole page, so diagram colours live in the reader CSS keyed off .kg-diagram instead.
        return safelist;
    }

    @Override
    public String sanitize(String html) {
        return DiagramAllowlist.stripNonStepStyles(Jsoup.clean(html == null ? "" : html, BLOG));
    }

    @Override
    public String sanitizeGenerated(String html) {
        var fragment = Jsoup.parseBodyFragment(html == null ? "" : html);
        // Generated content may contain cited HTTPS documentation, books and videos.
        // BLOG still strips javascript/data URLs and every tag/attribute outside the allowlist.
        return DiagramAllowlist.stripNonStepStyles(Jsoup.clean(fragment.body().html(), BLOG));
    }
}
