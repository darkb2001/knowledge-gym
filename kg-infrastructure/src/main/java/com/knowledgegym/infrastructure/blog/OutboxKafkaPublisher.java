package com.knowledgegym.infrastructure.blog;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Transactional outbox relay: claim rows in a short TX (no Kafka I/O under row locks),
 * then deliver outside the transaction and mark published in a follow-up TX.
 */
@Component
@ConditionalOnProperty(name = "app.blog.kafka.enabled", havingValue = "true")
public class OutboxKafkaPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public OutboxKafkaPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                                ObjectMapper json, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.json = json;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.blog.outbox.poll-ms:5000}")
    public void publishPending() {
        List<Pending> claimed = claimBatch();
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        for (Pending row : claimed) {
            if (row.attempts() >= 8) {
                sendToDlq(row, row.lastError() == null ? "Kafka delivery retry limit reached" : row.lastError());
                continue;
            }
            try {
                String topic = row.eventType().startsWith("collector.") ? "blog.collected" : "blog.published";
                kafka.send(topic, row.aggregateId().toString(), row.payload()).get(5, TimeUnit.SECONDS);
                markPublished(row.id());
            } catch (Exception error) {
                String message = error.getClass().getSimpleName() + ": "
                        + (error.getMessage() == null ? "delivery failed" : error.getMessage());
                recordFailure(row.id(), message.substring(0, Math.min(500, message.length())));
                if (row.attempts() >= 8) {
                    sendToDlq(row, message);
                }
            }
        }
    }

    /** Claim up to 25 pending rows and bump attempts so locks are not held during Kafka I/O. */
    private List<Pending> claimBatch() {
        return tx.execute(status -> {
            List<Pending> rows = jdbc.query(
                    "SELECT id,aggregate_id,event_type,payload::text,attempts,last_error FROM event_outbox "
                            + "WHERE published_at IS NULL ORDER BY occurred_at LIMIT 25 FOR UPDATE SKIP LOCKED",
                    (rs, n) -> new Pending(
                            rs.getObject("id", UUID.class),
                            rs.getObject("aggregate_id", UUID.class),
                            rs.getString("event_type"),
                            rs.getString("payload"),
                            rs.getInt("attempts") + 1,
                            rs.getString("last_error")));
            for (Pending row : rows) {
                jdbc.update("UPDATE event_outbox SET attempts=? WHERE id=?", row.attempts(), row.id());
            }
            return rows;
        });
    }

    private void markPublished(UUID id) {
        tx.executeWithoutResult(status ->
                jdbc.update("UPDATE event_outbox SET published_at=NOW(),last_error=NULL WHERE id=?", id));
    }

    private void recordFailure(UUID id, String message) {
        tx.executeWithoutResult(status ->
                jdbc.update("UPDATE event_outbox SET last_error=? WHERE id=?", message, id));
    }

    private void sendToDlq(Pending row, String error) {
        try {
            String payload = json.writeValueAsString(Map.of(
                    "eventId", row.id(),
                    "eventType", row.eventType(),
                    "aggregateId", row.aggregateId(),
                    "error", error,
                    "payload", json.readTree(row.payload())));
            kafka.send("blog.events.dlq", row.id().toString(), payload).get(5, TimeUnit.SECONDS);
            markPublished(row.id());
        } catch (Exception ignored) {
            /* Keep pending for a later retry; never discard an event if DLQ is unavailable. */
        }
    }

    private record Pending(UUID id, UUID aggregateId, String eventType, String payload, int attempts, String lastError) {}
}
