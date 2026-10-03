package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.port.AdminModerationPort;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

public class ModerateBlogUseCase {
    private final AdminModerationPort moderation;
    public ModerateBlogUseCase(AdminModerationPort moderation) { this.moderation = moderation; }
    public PageResult<AdminModerationPort.CommentView> comments(String q, UUID postId, UUID userId,
            AdminModerationPort.CommentStatus status, int page, int size) {
        if (page < 1 || size < 1 || size > 100 || q != null && q.length() > 200)
            throw new IllegalArgumentException("page >= 1, size 1..100, q tối đa 200 ký tự");
        return moderation.comments(q, postId, userId, status, page, size);
    }
    @Transactional
    public AdminModerationPort.CommentView comment(UUID actor, UUID id,
            AdminModerationPort.CommentStatus status, boolean restore, String reason) {
        reason(reason);
        if (status == null) throw new IllegalArgumentException("status bắt buộc");
        return moderation.moderateComment(actor, id, status, restore, reason.trim());
    }
    @Transactional
    public String post(UUID actor, UUID id, AdminModerationPort.PostAction action, String reason) {
        reason(reason);
        if (action == null) throw new IllegalArgumentException("action bắt buộc");
        return moderation.moderatePost(actor, id, action, reason.trim());
    }
    private static void reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("reason bắt buộc, tối đa 500 ký tự");
    }
}
