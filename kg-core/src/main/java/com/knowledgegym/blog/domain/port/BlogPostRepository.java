package com.knowledgegym.blog.domain.port;

import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.shared.domain.model.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository {
    List<BlogPost> findPublished(int offset, int limit, String tag);
    /** Tổng số bài PUBLISHED (theo tag nếu có) — dùng cho envelope phân trang public. */
    default long countPublished(String tag) { throw new UnsupportedOperationException(); }
    default PageResult<BlogPost> searchAdmin(String status, UUID moduleId, String q, int page, int size) {
        throw new UnsupportedOperationException();
    }
    Optional<BlogPost> findPublishedBySlug(String slug);
    Optional<BlogPost> findById(UUID id);
    BlogPost createDraft(UUID authorId, String title, String slug, String body, String excerpt,
                         UUID moduleId, UUID questionId, List<String> tags);
    void publish(UUID postId);
    List<Comment> comments(UUID postId);
    Comment addComment(UUID postId, UUID userId, UUID parentId, String body);
    boolean setLiked(UUID postId, UUID userId, boolean liked);
    void recordView(UUID postId, UUID userId);

    /**
     * Comment kèm tác giả (`authorDisplayName`/`authorAvatarUrl` đọc từ `users`). Hai field này
     * thêm sau, ở cuối record để không phá constructor hiện hữu.
     */
    record Comment(UUID id, UUID postId, UUID userId, UUID parentId, String content, java.time.Instant createdAt,
                   String authorDisplayName, String authorAvatarUrl) {}
}
