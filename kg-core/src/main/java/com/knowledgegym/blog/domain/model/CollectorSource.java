package com.knowledgegym.blog.domain.model;

import java.time.Instant;
import java.util.UUID;

public record CollectorSource(UUID id, String name, Type type, String url, String config,
                              int fetchIntervalSeconds, Instant lastFetchedAt) {
    public enum Type { RSS, API, SCRAPE }
}
