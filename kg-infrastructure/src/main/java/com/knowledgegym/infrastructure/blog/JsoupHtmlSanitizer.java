package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/** Strict allowlist aligned with answer HTML — no images, no javascript: URIs. */
@Component
public class JsoupHtmlSanitizer implements BlogPostsUseCase.HtmlSanitizer {
    private static final Safelist BLOG = Safelist.none()
            .addTags("p", "br", "hr", "strong", "b", "em", "i", "u", "s", "code", "pre",
                    "ul", "ol", "li", "blockquote", "small", "sup", "sub",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "dl", "dt", "dd", "span", "div", "a")
            .addAttributes("a", "href", "title")
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("span", "class")
            .addAttributes("div", "class")
            .addProtocols("a", "href", "http", "https", "mailto");

    @Override
    public String sanitize(String html) {
        return Jsoup.clean(html == null ? "" : html, BLOG);
    }
}
