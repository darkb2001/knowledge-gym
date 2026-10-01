package com.knowledgegym.blog.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BlogPost(UUID id, UUID authorId, String authorType, String title, String slug,
                       String body, String excerpt, UUID sourceModuleId, UUID sourceQuestionId,
                       String status, Instant publishedAt, int viewCount, int likeCount,
                       List<String> tags, Instant createdAt) {
    public BlogPost { tags = tags == null ? List.of() : List.copyOf(tags); }
}
