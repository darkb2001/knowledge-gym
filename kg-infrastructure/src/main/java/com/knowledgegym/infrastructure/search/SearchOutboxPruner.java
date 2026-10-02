package com.knowledgegym.infrastructure.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code search_outbox} bounded by deleting <em>processed</em> rows older than a retention
 * window.
 *
 * <p>Runs unconditionally, not behind {@code app.search.elasticsearch.enabled}. The capture
 * triggers write rows whether or not anything is draining them.
 *
 * <p><b>Only rows with {@code processed_at IS NOT NULL} are deleted.</b> While runtime mode is
 * {@code POSTGRES} the relay does not drain; pending rows must survive until Elasticsearch is
 * started again (or a full reindex runs). Deleting pending by age would permanently desync the
 * index from Postgres whenever reindex-on-startup is skipped because the cluster is down.
 *
 * <p>The outbox is still not the source of truth — a full reindex can rebuild from source tables —
 * but pending rows are the cheapest catch-up path after a long POSTGRES window.
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
                    "DELETE FROM search_outbox "
                            + "WHERE processed_at IS NOT NULL "
                            + "AND processed_at < NOW() - make_interval(days => ?)",
                    retentionDays);
            if (deleted > 0) {
                log.debug("Pruned {} processed rows from search_outbox", deleted);
            }
        } catch (RuntimeException e) {
            // Housekeeping must not take the app down or spam: the next tick tries again.
            log.warn("Failed to prune search_outbox", e);
        }
    }
}
