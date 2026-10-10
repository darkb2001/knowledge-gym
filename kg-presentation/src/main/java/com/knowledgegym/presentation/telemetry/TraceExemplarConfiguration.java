package com.knowledgegym.presentation.telemetry;

import io.opentelemetry.api.trace.Span;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reuse agent context; do not install a second SDK or duplicate instrumentation. */
@Configuration(proxyBeanMethods = false)
public class TraceExemplarConfiguration {
    @Bean
    SpanContext prometheusSpanContext() {
        return new SpanContext() {
            @Override public String getCurrentTraceId() {
                var context = Span.current().getSpanContext();
                return context.isValid() ? context.getTraceId() : null;
            }
            @Override public String getCurrentSpanId() {
                var context = Span.current().getSpanContext();
                return context.isValid() ? context.getSpanId() : null;
            }
            @Override public boolean isCurrentSpanSampled() {
                return Span.current().getSpanContext().isSampled();
            }
            @Override public void markCurrentSpanAsExemplar() {
                // Marking is advisory; no PII attribute or extra trace is created.
            }
        };
    }
}
