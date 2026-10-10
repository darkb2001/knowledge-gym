package com.knowledgegym.presentation.telemetry;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.helpers.MessageFormatter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Console-only JSON, with no arbitrary MDC/KVP or Throwable messages.
 * Dynamic strings/objects are omitted BEFORE formatting: regex alone cannot
 * recognise an unlabelled opaque password, payload, OAuth code or SQL value.
 */
public final class SafeJsonEncoder extends EncoderBase<ILoggingEvent> {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(?:[\\w.+%-]+@[\\w.-]+\\.[a-z]{2,}|\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
            + "|\\b[0-9a-f]{0,4}(?::[0-9a-f]{0,4}){2,}\\b"
            + "|https?://[^\\s]+|\\bBearer\\s+[^\\s]+|\\b[\\w+/=-]{32,}\\b|\\b\\d{4,8}\\b"
            + "|(?:password|secret|token|cookie|authorization|code|api[-_]?key)\\s*[=:]\\s*[^\\s,;]+)");
    private String service = "knowledge-gym";
    private String environment = "development";

    public void setService(String service) { this.service = boundedName(service); }
    public void setEnvironment(String environment) { this.environment = boundedName(environment); }

    @Override public byte[] headerBytes() { return null; }
    @Override public byte[] footerBytes() { return null; }

    @Override public byte[] encode(ILoggingEvent event) {
        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            fields.put("service", service);
            fields.put("environment", environment);
            fields.put("level", event.getLevel().toString());
            fields.put("logger", event.getLoggerName());
            fields.put("message", safeMessage(event));
            Map<String, String> mdc = event.getMDCPropertyMap();
            putMatching(fields, mdc, "trace_id", "[0-9a-f]{32}");
            putMatching(fields, mdc, "span_id", "[0-9a-f]{16}");
            putMatching(fields, mdc, "request_id", "[a-zA-Z0-9_-]{1,64}");
            putMatching(fields, mdc, "http_method", "GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS|OTHER");
            String route = mdc.get("http_route");
            if (route != null && route.length() <= 256 && !route.contains("?")
                    && !route.contains("\n") && !route.contains("\r")) {
                fields.put("http_route", route);
            }
            putMatching(fields, mdc, "http_status", "[1-5][0-9]{2}");
            putMatching(fields, mdc, "duration_ms", "[0-9]{1,12}");
            IThrowableProxy error = event.getThrowableProxy();
            if (error != null) fields.put("stack_trace", stack(error, new IdentityHashMap<>(), new int[]{128}, 0));
            return (JSON.writeValueAsString(fields) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            // Encoding failure must not throw into application work or echo the event.
            return "{\"level\":\"ERROR\",\"message\":\"telemetry_encoding_failed\"}\n"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    static String safeMessage(ILoggingEvent event) {
        // Libraries can concatenate payloads/secrets before calling SLF4J.
        if (event.getLoggerName() == null || !event.getLoggerName().startsWith("com.knowledgegym.")) {
            return "library_event";
        }
        Object[] arguments = event.getArgumentArray();
        Object[] safe = arguments == null ? null : new Object[arguments.length];
        if (arguments != null) for (int i = 0; i < arguments.length; i++) {
            Object value = arguments[i];
            safe[i] = value instanceof Number || value instanceof Boolean ? value : "[REDACTED]";
        }
        String template = event.getMessage() == null ? "" : event.getMessage();
        // Bound processing too, not just final output. Literals must be reviewed constants.
        template = template.substring(0, Math.min(template.length(), 4096));
        String formatted = MessageFormatter.arrayFormat(template, safe).getMessage();
        return SENSITIVE.matcher(formatted).replaceAll("[REDACTED]");
    }

    private static void putMatching(Map<String, Object> fields, Map<String, String> mdc,
                                    String name, String pattern) {
        String value = mdc.get(name);
        if (value != null && value.matches(pattern)) fields.put(name, value);
    }

    private static List<String> stack(IThrowableProxy error, IdentityHashMap<IThrowableProxy, Boolean> seen,
                                      int[] remainingFrames, int depth) {
        List<String> lines = new ArrayList<>();
        if (depth >= 8 || seen.size() >= 16 || seen.put(error, true) != null) {
            lines.add("[stack truncated]");
            return lines;
        }
        lines.add(boundedStackLine(error.getClassName())); // Never Throwable.getMessage().
        var frames = error.getStackTraceElementProxyArray();
        if (frames != null) {
            int count = Math.min(frames.length, remainingFrames[0]);
            remainingFrames[0] -= count;
            for (int i = 0; i < count; i++) {
                lines.add(boundedStackLine("at " + frames[i].getStackTraceElement()));
            }
            if (frames.length > count) lines.add("[frames truncated]");
        }
        if (error.getCause() != null) {
            lines.add("Caused by:");
            lines.addAll(stack(error.getCause(), seen, remainingFrames, depth + 1));
        }
        // Suppressed exceptions are bounded just like the cause chain.
        var suppressed = error.getSuppressed();
        if (suppressed != null) for (int i = 0; i < Math.min(suppressed.length, 4); i++) {
            lines.add("Suppressed:");
            lines.addAll(stack(suppressed[i], seen, remainingFrames, depth + 1));
        }
        return lines;
    }

    private static String boundedStackLine(String value) {
        return value.substring(0, Math.min(value.length(), 256));
    }

    private static String boundedName(String value) {
        return value != null && value.matches("[a-zA-Z0-9._-]{1,64}") ? value : "unknown";
    }
}
