package com.knowledgegym.presentation.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TraceExemplarConfigurationTest {
    @Test void sampledTraceBecomesOpenMetricsExemplarNotHighCardinalityMetricLabel() {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(),
                Clock.SYSTEM, new TraceExemplarConfiguration().prometheusSpanContext());
        try {
            Timer timer = Timer.builder("http.server.requests").tags("uri", "/notes/{id}")
                    .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofSeconds(1)).register(registry);
            var span = Span.wrap(SpanContext.create("1".repeat(32), "2".repeat(16),
                    TraceFlags.getSampled(), TraceState.getDefault()));
            try (var ignored = span.makeCurrent()) { timer.record(Duration.ofMillis(25)); }
            String output = registry.scrape("application/openmetrics-text; version=1.0.0");
            assertTrue(output.contains("trace_id=\"" + "1".repeat(32) + "\""));
            assertTrue(output.contains("span_id=\"" + "2".repeat(16) + "\""));
            assertTrue(output.contains(" # {"), "ID must be exemplar, not time-series label");
            assertFalse(output.lines().filter(line -> !line.startsWith("#"))
                    .map(line -> line.split(" # ")[0]).anyMatch(line -> line.contains("trace_id")));
        } finally {
            registry.close();
        }
    }
}
