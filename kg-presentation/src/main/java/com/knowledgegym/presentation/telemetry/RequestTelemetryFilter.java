package com.knowledgegym.presentation.telemetry;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** After the agent's server span, before Spring Security; never reads bodies/queries/headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class RequestTelemetryFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTelemetryFilter.class);
    private static final String STATE = RequestTelemetryFilter.class.getName() + ".state";
    private record State(String requestId, long startNanos, AtomicBoolean logged) {}

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        State state = (State) request.getAttribute(STATE);
        if (state == null) {
            // Nginx replaces client supplied IDs. Direct internal callers still get validation.
            String supplied = request.getHeader("X-Request-ID");
            String id = supplied != null && supplied.matches("[0-9a-f]{32}") ? supplied
                    : UUID.randomUUID().toString().replace("-", "");
            state = new State(id, System.nanoTime(), new AtomicBoolean());
            request.setAttribute(STATE, state);
        }
        Map<String, String> previous = MDC.getCopyOfContextMap();
        boolean failed = false;
        try {
            MDC.put("request_id", state.requestId());
            var span = Span.current().getSpanContext();
            if (span.isValid()) {
                MDC.put("trace_id", span.getTraceId());
                MDC.put("span_id", span.getSpanId());
                response.setHeader("X-Trace-ID", span.getTraceId());
            } else {
                MDC.remove("trace_id");
                MDC.remove("span_id");
            }
            response.setHeader("X-Request-ID", state.requestId());
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error error) {
            failed = true;
            throw error;
        } finally {
            try {
                // Async MVC dispatch logs at completion, not before the response is ready.
                if (!request.isAsyncStarted() && state.logged().compareAndSet(false, true)) {
                    String method = request.getMethod();
                    MDC.put("http_method", method.matches("GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS")
                            ? method : "OTHER");
                    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    MDC.put("http_route", route == null ? "_unmatched" : route.toString());
                    MDC.put("http_status", Integer.toString(failed ? 500 : response.getStatus()));
                    MDC.put("duration_ms", Long.toString((System.nanoTime() - state.startNanos()) / 1_000_000));
                    if (failed || response.getStatus() >= 500) LOG.error("http_request_completed");
                    else LOG.info("http_request_completed");
                }
            } finally {
                if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
            }
        }
    }
}
