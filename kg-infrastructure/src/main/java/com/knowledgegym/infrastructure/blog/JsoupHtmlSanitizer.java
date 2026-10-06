package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/** Strict allowlist for readable technical articles; external links/images remain HTTPS-only. */
@Component
public class JsoupHtmlSanitizer implements BlogPostsUseCase.HtmlSanitizer {
    private static final Safelist BLOG = Safelist.none()
            .addTags("p", "br", "hr", "strong", "b", "em", "i", "u", "s", "code", "pre",
                    "ul", "ol", "li", "blockquote", "small", "sup", "sub",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "dl", "dt", "dd", "span", "div", "a", "figure", "figcaption", "img")
            .addAttributes("a", "href", "title", "rel")
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("span", "class")
            .addAttributes("div", "class")
            .addAttributes("figure", "class")
            .addAttributes("img", "src", "alt", "title", "loading")
            .addProtocols("a", "href", "https")
            .addProtocols("img", "src", "https");

    @Override
    public String sanitize(String html) {
        return Jsoup.clean(html == null ? "" : html, BLOG);
    }

    @Override
    public String sanitizeGenerated(String html) {
        var fragment = Jsoup.parseBodyFragment(html == null ? "" : html);
        // Generated content may contain cited HTTPS documentation, books and videos.
        // BLOG still strips javascript/data URLs and every tag/attribute outside the allowlist.
        return Jsoup.clean(fragment.body().html(), BLOG);
    }
}
