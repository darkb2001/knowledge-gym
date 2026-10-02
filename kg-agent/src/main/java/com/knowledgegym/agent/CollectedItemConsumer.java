package com.knowledgegym.agent;

import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.blog.domain.port.CollectorRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name="app.blog.kafka.enabled", havingValue="true")
public class CollectedItemConsumer {
    private final ObjectMapper json;
    private final CollectorRepository repository;
    public CollectedItemConsumer(ObjectMapper json,CollectorRepository repository){this.json=json;this.repository=repository;}

    /** Idempotent projection work: redelivery recomputes score/category from persisted normalized fields. */
    @KafkaListener(topics="blog.collected",groupId="blog-collector-score-v1")
    public void onCollected(String message) throws Exception {
        String rawId=json.readTree(message).path("itemId").asText();
        if(rawId.isBlank())throw new IllegalArgumentException("collector event missing itemId");
        UUID itemId=UUID.fromString(rawId);
        repository.scoreItem(itemId);
    }
}
