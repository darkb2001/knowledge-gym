package com.knowledgegym.presentation.rest.blog;

import com.knowledgegym.blog.application.BlogPostsUseCase;
import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.BlogPostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/blog")
public class BlogController {
    private final BlogPostsUseCase blog;
    private final String canonicalBaseUrl;

    public BlogController(BlogPostsUseCase blog,
                          @Value("${app.blog.canonical-base-url:http://localhost:3000}") String canonicalBaseUrl) {
        this.blog = blog;
        this.canonicalBaseUrl = canonicalBaseUrl.replaceAll("/$", "");
    }

    /** List card — omit body to keep payloads small. */
    public record PostSummary(UUID id, String title, String slug, String excerpt, Instant publishedAt,
                              int viewCount, int likeCount, List<String> tags) {
        static PostSummary of(BlogPost p) {
            return new PostSummary(p.id(), p.title(), p.slug(), p.excerpt(), p.publishedAt(),
                    p.viewCount(), p.likeCount(), p.tags());
        }
    }

    public record Post(UUID id, String title, String slug, String body, String excerpt, String status,
                       Instant publishedAt, int viewCount, int likeCount, List<String> tags, Instant createdAt) {
        static Post of(BlogPost p) {
            return new Post(p.id(), p.title(), p.slug(), p.body(), p.excerpt(), p.status(), p.publishedAt(),
                    p.viewCount(), p.likeCount(), p.tags(), p.createdAt());
        }
    }

    public record Comment(UUID id, UUID userId, UUID parentId, String content, Instant createdAt) {
        static Comment of(BlogPostRepository.Comment c) {
            return new Comment(c.id(), c.userId(), c.parentId(), c.content(), c.createdAt());
        }
    }

    public record CommentRequest(UUID parentId, String content) {}

    public record LikeResult(boolean liked, int likeCount) {}

    @GetMapping("/posts")
    public List<PostSummary> list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String tag) {
        return blog.list(page, size, tag).stream().map(PostSummary::of).toList();
    }

    @GetMapping("/posts/{slug}")
    public Post detail(@PathVariable String slug) {
        return Post.of(blog.get(slug));
    }

    @GetMapping("/posts/{slug}/comments")
    public List<Comment> comments(@PathVariable String slug) {
        return blog.comments(slug).stream().map(Comment::of).toList();
    }

    @PostMapping("/posts/{slug}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Comment comment(@PathVariable String slug,
                           @AuthenticationPrincipal UUID userId,
                           @RequestBody CommentRequest request) {
        return Comment.of(blog.addComment(slug, userId, request.parentId(), request.content()));
    }

    @PostMapping("/posts/{slug}/like")
    public LikeResult like(@PathVariable String slug, @AuthenticationPrincipal UUID userId) {
        boolean liked = blog.setLiked(slug, userId, true);
        return new LikeResult(liked, blog.get(slug).likeCount());
    }

    @DeleteMapping("/posts/{slug}/like")
    public LikeResult unlike(@PathVariable String slug, @AuthenticationPrincipal UUID userId) {
        boolean liked = blog.setLiked(slug, userId, false);
        return new LikeResult(liked, blog.get(slug).likeCount());
    }

    @PostMapping("/posts/{slug}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@PathVariable String slug, @AuthenticationPrincipal UUID userId) {
        blog.recordView(slug, userId);
    }

    @GetMapping(value = "/feed.rss", produces = MediaType.APPLICATION_XML_VALUE)
    public String feed() {
        List<BlogPost> posts = blog.list(1, 50, null);
        String self = canonicalBaseUrl + "/blog/feed.rss";
        String items = posts.stream()
                .map(p -> "<item><title>" + xml(p.title()) + "</title><link>"
                        + xml(canonicalBaseUrl + "/blog/" + p.slug()) + "</link><guid isPermaLink=\"false\">"
                        + p.id() + "</guid><description>" + xml(p.excerpt()) + "</description><pubDate>"
                        + DateTimeFormatter.RFC_1123_DATE_TIME.format(p.publishedAt().atZone(ZoneOffset.UTC))
                        + "</pubDate></item>")
                .reduce("", String::concat);
        String lastBuildDate = posts.isEmpty() ? ""
                : "<lastBuildDate>" + DateTimeFormatter.RFC_1123_DATE_TIME
                        .format(posts.getFirst().publishedAt().atZone(ZoneOffset.UTC)) + "</lastBuildDate>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">"
                + "<channel><title>Knowledge Gym</title><link>"
                + xml(canonicalBaseUrl + "/blog") + "</link><description>Knowledge Gym blog</description>"
                + "<language>vi</language><ttl>60</ttl>"
                + "<atom:link href=\"" + xml(self) + "\" rel=\"self\" type=\"application/rss+xml\"/>"
                + lastBuildDate + items + "</channel></rss>";
    }

    private static String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
