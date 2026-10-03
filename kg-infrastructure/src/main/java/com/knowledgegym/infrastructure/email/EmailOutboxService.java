package com.knowledgegym.infrastructure.email;

import com.knowledgegym.identity.domain.port.EmailService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka-backed mail port. It writes to the existing PostgreSQL transactional outbox;
 * the relay publishes only after the row is committed. Plaintext codes are required
 * to render the message, so the outbox must be access-controlled and short-lived.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.email.kafka-enabled", havingValue = "true")
public class EmailOutboxService implements EmailService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public EmailOutboxService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void sendEmailVerificationCode(String email, String code) {
        enqueue("email.verification", email, code);
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        enqueue("email.password-reset", email, code);
    }

    private void enqueue(String eventType, String email, String code) {
        try {
            String payload = json.writeValueAsString(Map.of("email", email, "code", code));
            jdbc.update("""
                INSERT INTO event_outbox(aggregate_type, aggregate_id, event_type, payload)
                VALUES ('email', ?, ?, ?::jsonb)
                """, UUID.randomUUID(), eventType, payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to queue email delivery", ex);
        }
    }
}
