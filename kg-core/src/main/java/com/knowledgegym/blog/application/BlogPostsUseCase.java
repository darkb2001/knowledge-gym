package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.BlogPostRepository;
import com.knowledgegym.shared.application.NotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BlogPostsUseCase {
    private final BlogPostRepository posts;
    private final HtmlSanitizer sanitizer;

    public BlogPostsUseCase(BlogPostRepository posts, HtmlSanitizer sanitizer) {
        this.posts = posts;
        this.sanitizer = sanitizer;
    }

    public List<BlogPost> list(int page, int size, String tag) {
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("page/size không hợp lệ");
        return posts.findPublished((page - 1) * size, size, cleanTag(tag));
    }
    public BlogPost get(String slug) { return posts.findPublishedBySlug(slug).orElseThrow(() -> new NotFoundException("Bài viết không tồn tại")); }
    public BlogPost createDraft(UUID adminId, String title, String body, UUID moduleId, UUID questionId, List<String> tags) {
        if (title == null || title.isBlank() || title.length() > 300) throw new IllegalArgumentException("title không hợp lệ");
        if (body == null || body.isBlank() || body.length() > 250_000) throw new IllegalArgumentException("body không hợp lệ");
        String safeBody = sanitizer.sanitize(body);
        if (safeBody.isBlank()) throw new IllegalArgumentException("Nội dung không có sau khi sanitize");
        String excerpt = safeBody.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 300) excerpt = excerpt.substring(0, 300);
        String slug = slug(title);
        return posts.createDraft(adminId, title.trim(), slug, safeBody, excerpt, moduleId, questionId,
                tags == null ? List.of() : tags.stream().filter(t -> t != null && !t.isBlank()).map(BlogPostsUseCase::cleanTag).distinct().limit(20).toList());
    }
    public void publish(UUID id) {
        BlogPost post = posts.findById(id).orElseThrow(() -> new NotFoundException("Bài viết không tồn tại"));
        if (!"DRAFT".equals(post.status()) && !"REVIEW".equals(post.status())) throw new IllegalArgumentException("Chỉ có thể publish bài ở trạng thái DRAFT/REVIEW");
        posts.publish(id);
    }
    public List<BlogPostRepository.Comment> comments(String slug) { return posts.comments(get(slug).id()); }
    public BlogPostRepository.Comment addComment(String slug, UUID user, UUID parentId, String content) {
        BlogPost post = get(slug);
        if (content == null || content.length() > 5_000) throw new IllegalArgumentException("comment không hợp lệ");
        String safe = sanitizer.sanitize(content);
        if (safe == null || safe.isBlank()) throw new IllegalArgumentException("comment không hợp lệ");
        return posts.addComment(post.id(), user, parentId, safe);
    }
    public boolean setLiked(String slug, UUID user, boolean liked) { return posts.setLiked(get(slug).id(), user, liked); }
    public void recordView(String slug, UUID user) { posts.recordView(get(slug).id(), user); }

    private static String slug(String title) {
        String normalized = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) normalized = "post";
        return normalized.substring(0, Math.min(280, normalized.length())) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    private static String cleanTag(String tag) {
        if (tag == null) return null;
        String safe = tag.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}-]", "");
        return safe.substring(0, Math.min(50, safe.length()));
    }
    public interface HtmlSanitizer {
        String sanitize(String html);
        default String sanitizeGenerated(String html) { return sanitize(html); }
    }
}
