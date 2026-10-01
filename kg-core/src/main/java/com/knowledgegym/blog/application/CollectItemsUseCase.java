package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.model.CollectedItem;
import com.knowledgegym.blog.domain.model.CollectorSource;
import com.knowledgegym.blog.domain.port.CollectorFeed;
import com.knowledgegym.blog.domain.port.CollectorRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Scheduled/manual collector orchestration. Storage uniqueness guarantees dedup across restarts. */
public final class CollectItemsUseCase {
    private final CollectorRepository repository;
    private final CollectorFeed feed;

    public CollectItemsUseCase(CollectorRepository repository, CollectorFeed feed) {
        this.repository = repository;
        this.feed = feed;
    }

    public Result execute() {
        UUID runId = repository.startRun("due collector sources");
        int sources = 0;
        int inserted = 0;
        int failed = 0;
        StringBuilder failures = new StringBuilder();
        try {
            for (CollectorSource source : repository.findDueSources()) {
                sources++;
                try {
                    List<CollectedItem> items = feed.fetch(source).stream()
                            .filter(item -> item.title() != null && !item.title().isBlank() && item.url() != null && !item.url().isBlank())
                            .map(CollectItemsUseCase::normalizeUrl)
                            .map(CollectItemsUseCase::withHash)
                            .map(CollectItemsUseCase::score)
                            .toList();
                    // saveCollected persists items+outbox and marks the source fetched in one TX.
                    inserted += repository.saveCollected(source.id(), items);
                } catch (Exception sourceFailure) {
                    failed++;
                    failures.append(source.name()).append(": ").append(safeMessage(sourceFailure)).append("; ");
                }
            }
            Result result = new Result(sources, inserted, failed);
            repository.finishRun(runId, failed == 0,
                    "sources=" + sources + ", inserted=" + inserted + ", failed=" + failed,
                    failed == 0 ? null : failures.substring(0, Math.min(500, failures.length())));
            return result;
        } catch (Exception failure) {
            repository.finishRun(runId, false, "sources=" + sources + ", inserted=" + inserted,
                    safeMessage(failure));
            throw new IllegalStateException("Collector run failed", failure);
        }
    }

    private static CollectedItem normalizeUrl(CollectedItem item) {
        String raw = item.url().trim();
        try {
            java.net.URI uri = java.net.URI.create(raw);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath().replaceAll("/+$", "");
            if (path.isBlank()) path = "/";
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            String normalized = scheme + "://" + host + path + query;
            return new CollectedItem(item.title(), normalized, item.summary(), item.contentHash(),
                    item.publishedAt(), item.tags(), item.score(), item.category());
        } catch (IllegalArgumentException e) {
            return item;
        }
    }

    private static CollectedItem withHash(CollectedItem item) {
        if (item.contentHash() != null && !item.contentHash().isBlank()) return item;
        try {
            String source = (item.title() + "\n" + (item.summary() == null ? "" : item.summary()))
                    .toLowerCase(Locale.ROOT).trim();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
            return new CollectedItem(item.title(), item.url(), item.summary(), hash, item.publishedAt(), item.tags(), item.score(), item.category());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CollectedItem score(CollectedItem item) {
        String text = (item.title() + " " + (item.summary() == null ? "" : item.summary())).toLowerCase(Locale.ROOT);
        String category = text.contains("spring") ? "spring" : text.contains("database") || text.contains("sql") || text.contains("hibernate") ? "database" : text.contains("kafka") || text.contains("microservice") ? "architecture" : "java";
        int score = 40;
        for (String keyword : List.of("java", "spring", "jvm", "security", "database", "performance", "kafka", "release")) {
            if (text.contains(keyword)) score += 7;
        }
        if (item.publishedAt() != null && item.publishedAt().isAfter(Instant.now().minus(30, ChronoUnit.DAYS))) score += 10;
        score = Math.min(100, score);
        return new CollectedItem(item.title(), item.url(), item.summary(), item.contentHash(), item.publishedAt(), item.tags(), score, category);
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.substring(0, Math.min(500, message.length()));
    }

    public record Result(int sourcesFetched, int itemsInserted, int failedSources) {}
}
