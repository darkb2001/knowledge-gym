package com.knowledgegym.infrastructure.email;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Kafka mail consumer. SMTP failure is thrown so the configured Kafka retry/DLQ policy applies. */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.email.kafka-enabled", havingValue = "true")
public class EmailKafkaConfiguration {
    static final String VERIFICATION_TOPIC = "email.verification";
    static final String PASSWORD_RESET_TOPIC = "email.password-reset";

    @Bean NewTopic emailVerificationTopic() {
        return new NewTopic(VERIFICATION_TOPIC, 3, (short) 1);
    }

    @Bean NewTopic passwordResetTopic() {
        return new NewTopic(PASSWORD_RESET_TOPIC, 3, (short) 1);
    }

    @Bean NewTopic emailDlqTopic() {
        return new NewTopic("email.events.dlq", 3, (short) 1);
    }

    @Bean
    EmailKafkaConsumer emailKafkaConsumer(GmailEmailService smtp, ObjectMapper json) {
        return new EmailKafkaConsumer(smtp, json);
    }

    static final class EmailKafkaConsumer {
        private final GmailEmailService smtp;
        private final ObjectMapper json;

        EmailKafkaConsumer(GmailEmailService smtp, ObjectMapper json) {
            this.smtp = smtp;
            this.json = json;
        }

        @KafkaListener(topics = VERIFICATION_TOPIC, groupId = "knowledge-gym-email")
        void verification(String payload) throws Exception {
            JsonNode event = json.readTree(payload);
            smtp.sendEmailVerificationCode(required(event, "email"), required(event, "code"));
        }

        @KafkaListener(topics = PASSWORD_RESET_TOPIC, groupId = "knowledge-gym-email")
        void passwordReset(String payload) throws Exception {
            JsonNode event = json.readTree(payload);
            smtp.sendPasswordResetCode(required(event, "email"), required(event, "code"));
        }

        private static String required(JsonNode event, String field) {
            JsonNode value = event.get(field);
            if (value == null || value.isNull() || value.asString().isBlank()) {
                throw new IllegalArgumentException("Missing email event field: " + field);
            }
            return value.asString();
        }
    }
}
