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
            .addAttributes("a", "href", "title", "rel")
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

    @Override
    public String sanitizeGenerated(String html) {
        var fragment = Jsoup.parseBodyFragment(html == null ? "" : html);
        // Model-invented links are untrusted; canonical source links are attached by the application.
        fragment.select("a").unwrap();
        for(var heading:fragment.select("h1,h2,h3,h4,h5,h6").stream().toList()){
            String label=heading.text().trim().toLowerCase(java.util.Locale.ROOT);
            if(java.util.Set.of("sources","references","source","nguồn","tài liệu tham khảo","tham khảo").contains(label)){
                var next=heading.nextElementSibling();heading.remove();
                while(next!=null&&!next.tagName().matches("h[1-6]")){var after=next.nextElementSibling();next.remove();next=after;}
            }
        }
        return Jsoup.clean(fragment.body().html(), BLOG);
    }
}
