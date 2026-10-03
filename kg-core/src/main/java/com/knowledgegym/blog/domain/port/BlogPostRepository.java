package com.knowledgegym.blog.domain.port;

import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.shared.domain.model.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository {
    List<BlogPost> findPublished(int offset, int limit, String tag);
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

    record Comment(UUID id, UUID postId, UUID userId, UUID parentId, String content, java.time.Instant createdAt) {}
}
