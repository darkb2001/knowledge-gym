package com.knowledgegym.presentation.rest.blog;

import com.knowledgegym.blog.domain.port.BlogPostRepository;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/admin/blog/posts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlogListController {
    private final BlogPostRepository posts;
    public AdminBlogListController(BlogPostRepository posts) { this.posts = posts; }
    public record PostItem(UUID id, String title, String slug, String status, String excerpt,
                           UUID moduleId, UUID questionId, Instant createdAt, Instant publishedAt) {}
    @GetMapping
    public PageResponse<PostItem> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) UUID moduleId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(posts.searchAdmin(status, moduleId, q, page, size), post ->
                new PostItem(post.id(), post.title(), post.slug(), post.status(), post.excerpt(),
                        post.sourceModuleId(), post.sourceQuestionId(), post.createdAt(), post.publishedAt()));
    }
}
