package com.knowledgegym.presentation.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestTelemetryFilterTest {
    private final RequestTelemetryFilter filter = new RequestTelemetryFilter();
    private final ListAppender<ILoggingEvent> events = new ListAppender<>() {
        @Override protected void append(ILoggingEvent event) { event.prepareForDeferredProcessing(); super.append(event); }
    };
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestTelemetryFilter.class);
    private Level previous;
    @BeforeEach void setup() { previous = logger.getLevel(); logger.setLevel(Level.INFO); events.start(); logger.addAppender(events); MDC.clear(); }
    @AfterEach void clean() { logger.detachAppender(events); logger.setLevel(previous); MDC.clear(); }

    @Test void retainsSafeEdgeIdTemplateAndTraceButNeverRawPathQueryOrHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notes/private-identifier");
        request.setQueryString("token=synthetic-secret");
        request.addHeader("Authorization", "Bearer synthetic-secret");
        request.addHeader("X-Request-ID", "3".repeat(32));
        var response = new MockHttpServletResponse();
        MDC.put("existing", "value");
        var parent = Span.wrap(SpanContext.create("1".repeat(32), "2".repeat(16),
                TraceFlags.getSampled(), TraceState.getDefault()));
        try (var ignored = parent.makeCurrent()) {
            filter.doFilter(request, response, (req, res) -> {
                assertEquals("1".repeat(32), MDC.get("trace_id"));
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/notes/{id}");
                response.setStatus(404);
            });
        }
        assertEquals("3".repeat(32), response.getHeader("X-Request-ID"));
        assertEquals("1".repeat(32), response.getHeader("X-Trace-ID"));
        assertEquals(Map.of("existing", "value"), MDC.getCopyOfContextMap());
        var record = events.list.getFirst().getMDCPropertyMap();
        assertEquals("/notes/{id}", record.get("http_route"));
        assertEquals("404", record.get("http_status"));
        assertFalse(record.toString().contains("synthetic"));
        assertFalse(record.toString().contains("private-identifier"));
    }

    @Test void replacesOversizedInjectedIdsAndClearsContextOnException() {
        var request = new MockHttpServletRequest("POST", "/auth");
        request.addHeader("X-Request-ID", "user@example.test\r\n" + "a".repeat(1000));
        var response = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (req, res) -> { throw new ServletException("synthetic-password"); }));
        assertTrue(response.getHeader("X-Request-ID").matches("[0-9a-f]{32}"));
        assertNull(MDC.get("request_id"));
        assertEquals("500", events.list.getFirst().getMDCPropertyMap().get("http_status"));
        assertEquals(Level.ERROR, events.list.getFirst().getLevel());
    }

    @Test void secondRequestAndNoAgentHaveNoStaleTrace() throws Exception {
        MDC.put("trace_id", "1".repeat(32)); MDC.put("span_id", "2".repeat(16));
        var request = new MockHttpServletRequest("GET", "/missing?token=synthetic-secret");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertNull(MDC.get("trace_id")));
        assertEquals("_unmatched", events.list.getFirst().getMDCPropertyMap().get("http_route"));
        assertFalse(events.list.getFirst().getMDCPropertyMap().containsKey("trace_id"));
    }

    @Test void asyncRedispatchLogsExactlyOnceAtCompletion() throws Exception {
        var request = new MockHttpServletRequest("GET", "/async");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> request.startAsync(request, response));
        assertTrue(events.list.isEmpty());
        request.setAsyncStarted(false); request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, (req, res) -> response.setStatus(201));
        assertEquals(1, events.list.size());
        assertEquals("201", events.list.getFirst().getMDCPropertyMap().get("http_status"));
        assertNull(MDC.get("request_id"));
    }

    @Test void w3cValidAndMalformedHeadersAndExemplarContext() {
        var propagation = W3CTraceContextPropagator.getInstance();
        TextMapGetter<Map<String, String>> getter = new TextMapGetter<>() {
            @Override public Iterable<String> keys(Map<String, String> c) { return c.keySet(); }
            @Override public String get(Map<String, String> c, String key) { return c.get(key); }
        };
        String trace = "1".repeat(32);
        Context context = propagation.extract(Context.root(),
                Map.of("traceparent", "00-" + trace + "-" + "2".repeat(16) + "-01", "tracestate", "kg=canary"), getter);
        var exemplar = new TraceExemplarConfiguration().prometheusSpanContext();
        try (var ignored = context.makeCurrent()) {
            assertEquals(trace, exemplar.getCurrentTraceId());
            assertTrue(exemplar.isCurrentSpanSampled());
            assertEquals("canary", Span.current().getSpanContext().getTraceState().get("kg"));
        }
        for (String bad : new String[]{"invalid", "x".repeat(1000), "00-" + "0".repeat(32) + "-" + "0".repeat(16) + "-01"}) {
            Context malformed = propagation.extract(Context.root(), Map.of("traceparent", bad), getter);
            assertFalse(Span.fromContext(malformed).getSpanContext().isValid());
        }
        assertFalse(exemplar.isCurrentSpanSampled());
    }

    @Test void logLevelCanChangeWithoutRebuildAndDoesNotExposeAdditionalData() throws Exception {
        logger.setLevel(Level.ERROR);
        filter.doFilter(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), (req, res) -> {});
        assertTrue(events.list.isEmpty());
        logger.setLevel(Level.INFO);
        filter.doFilter(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), (req, res) -> {});
        assertEquals(1, events.list.size());
    }
}
