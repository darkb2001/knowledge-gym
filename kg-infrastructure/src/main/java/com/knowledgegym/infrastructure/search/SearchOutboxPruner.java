package com.knowledgegym.infrastructure.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code search_outbox} bounded by deleting rows older than a retention window.
 *
 * <p>Runs unconditionally, not behind {@code app.search.elasticsearch.enabled}. The capture
 * triggers write rows whether or not anything is draining them, so with the feature disabled the
 * table would otherwise grow without limit — and unbounded growth on a transactional write path is
 * a worse failure than the feature being absent.
 *
 * <p>The cut-off is on {@code occurred_at} and applies to pending rows too, not only processed
 * ones. A row still pending after the whole retention window means no relay is draining (flag off,
 * or the relay is wedged); keeping it would defeat the point. This is safe because the index is
 * rebuilt from the source tables on startup, so a purged row is recoverable — the outbox is a
 * bounded work queue, never the source of truth. Normal operation processes rows within seconds,
 * so the window is orders of magnitude larger than the real backlog.
 */
@Component
public class SearchOutboxPruner {

    private static final Logger log = LoggerFactory.getLogger(SearchOutboxPruner.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public SearchOutboxPruner(JdbcTemplate jdbc,
                              @Value("${app.search.outbox.retention-days:7}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${app.search.outbox.prune-ms:3600000}", initialDelayString = "60000")
    public void prune() {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM search_outbox WHERE occurred_at < NOW() - make_interval(days => ?)",
                    retentionDays);
            if (deleted > 0) {
                log.debug("Pruned {} rows from search_outbox", deleted);
            }
        } catch (RuntimeException e) {
            // Housekeeping must not take the app down or spam: the next tick tries again.
            log.warn("Failed to prune search_outbox", e);
        }
    }
}
