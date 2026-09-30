package com.knowledgegym.content.domain.model;

/** Module kèm số câu hỏi + slug topic — dùng cho `GET /modules`. */
public record ModuleWithStats(ModuleRef module, String topicSlug, long questionCount) {
}
