package com.knowledgegym.presentation.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/** Real embedded Kafka (no Docker) + agent, producer → headers → Spring consumer scope. */
class KafkaAgentPropagationTest {
    private record Received(String header, String stateHeader, SpanContext consumer, boolean baggageAbsent) {}

    @Test void producerConsumerPreserveW3cContextAndDoNotSendBaggage() throws Exception {
        var broker = new EmbeddedKafkaKraftBroker(1, 1, "telemetry-canary");
        broker.brokerProperties(Map.of("num.network.threads", "1", "num.io.threads", "1",
                "offsets.topic.replication.factor", "1"));
        broker.afterPropertiesSet();
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000));
        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "telemetry-canary",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        var properties = new ContainerProperties("telemetry-canary");
        var received = new CompletableFuture<Received>();
        properties.setMessageListener((MessageListener<String, String>) record -> {
            var parent = record.headers().lastHeader("traceparent");
            var state = record.headers().lastHeader("tracestate");
            received.complete(new Received(parent == null ? "" : new String(parent.value(), StandardCharsets.UTF_8),
                    state == null ? "" : new String(state.value(), StandardCharsets.UTF_8),
                    Span.current().getSpanContext(), record.headers().lastHeader("baggage") == null));
        });
        var container = new KafkaMessageListenerContainer<>(consumerFactory, properties);
        var kafka = new KafkaTemplate<>(producerFactory);
        try {
            container.start();
            var parent = Span.wrap(SpanContext.create("1".repeat(32), "2".repeat(16),
                    TraceFlags.getSampled(), TraceState.builder().put("kg", "canary").build()));
            try (var ignored = parent.makeCurrent();
                 var baggage = Baggage.builder().put("private", "synthetic-do-not-export").build().makeCurrent()) {
                kafka.send("telemetry-canary", "synthetic").get(10, TimeUnit.SECONDS);
            }
            Received result = received.get(20, TimeUnit.SECONDS);
            assertTrue(result.header().matches("00-" + "1".repeat(32) + "-[0-9a-f]{16}-01"));
            assertTrue(result.stateHeader().contains("kg=canary"));
            assertTrue(result.consumer().isValid());
            assertEquals("1".repeat(32), result.consumer().getTraceId());
            assertEquals("canary", result.consumer().getTraceState().get("kg"));
            assertTrue(result.baggageAbsent());
            assertFalse(Span.current().getSpanContext().isValid(), "consumer scope must not leak to caller");
        } finally {
            container.stop(); kafka.destroy(); producerFactory.destroy(); broker.destroy();
        }
    }
}
