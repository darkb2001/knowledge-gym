package com.knowledgegym.blog.domain.port;

import com.knowledgegym.shared.domain.model.PageResult;
import java.time.Instant;
import java.util.UUID;

public interface AdminModerationPort {
    enum CommentStatus { VISIBLE, HIDDEN, DELETED }
    enum PostAction { HIDE, ARCHIVE, DELETE, RESTORE }
    record CommentView(UUID id, UUID postId, String postTitle, UUID userId, String displayName,
                       UUID parentId, String content, CommentStatus status, Instant createdAt) {}
    PageResult<CommentView> comments(String q, UUID postId, UUID userId, CommentStatus status, int page, int size);
    CommentView moderateComment(UUID actor, UUID id, CommentStatus status, boolean restore, String reason);
    String moderatePost(UUID actor, UUID id, PostAction action, String reason);
}
