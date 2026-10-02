package com.knowledgegym.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Idempotent fan-out subscriber for {@code blog.published.v1}.
 * M9 only acknowledges the contract; M10/Hermes adapters may extend this bean.
 */
@Component
@ConditionalOnProperty(name = "app.blog.kafka.enabled", havingValue = "true")
public class BlogPublishedConsumer {
    private static final Logger log = LoggerFactory.getLogger(BlogPublishedConsumer.class);
    private final ObjectMapper json;

    public BlogPublishedConsumer(ObjectMapper json) {
        this.json = json;
    }

    @KafkaListener(topics = "blog.published", groupId = "blog-published-ack-v1")
    public void onPublished(String message) throws Exception {
        JsonNode event = json.readTree(message);
        String postId = event.path("postId").asText();
        String slug = event.path("slug").asText();
        String canonicalUrl = event.path("canonicalUrl").asText();
        if (postId.isBlank() || slug.isBlank()) {
            throw new IllegalArgumentException("blog.published event missing postId/slug");
        }
        log.info("blog.published received postId={} slug={} canonicalUrl={}", postId, slug, canonicalUrl);
    }
}
