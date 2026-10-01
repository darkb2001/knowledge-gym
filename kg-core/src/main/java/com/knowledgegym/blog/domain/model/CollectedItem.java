package com.knowledgegym.blog.domain.model;

import java.time.Instant;
import java.util.List;

public record CollectedItem(String title, String url, String summary, String contentHash,
                            Instant publishedAt, List<String> tags, double score, String category) {
    public CollectedItem { tags = tags == null ? List.of() : List.copyOf(tags); }
}
