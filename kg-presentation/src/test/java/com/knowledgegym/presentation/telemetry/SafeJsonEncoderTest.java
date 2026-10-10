package com.knowledgegym.presentation.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

class SafeJsonEncoderTest {
    private final SafeJsonEncoder encoder = new SafeJsonEncoder();
    private final Logger logger = (Logger) LoggerFactory.getLogger("com.knowledgegym.test");

    private LoggingEvent event(String message, Object... arguments) {
        LoggingEvent event = new LoggingEvent(getClass().getName(), logger, Level.ERROR, message, null, arguments);
        event.setMDCPropertyMap(Map.of("trace_id", "1".repeat(32), "span_id", "2".repeat(16),
                "request_id", "3".repeat(32), "authorization", "synthetic-mdc-secret",
                "http_route", "/notes/{id}", "http_status", "500"));
        return event;
    }
    private String encode(LoggingEvent event) { return new String(encoder.encode(event), StandardCharsets.UTF_8); }
    private String withMdc(String name, String value) {
        LoggingEvent event = new LoggingEvent(getClass().getName(), logger, Level.ERROR, "actor_event", null, new Object[0]);
        event.setMDCPropertyMap(Map.of(name, value));
        return encode(event);
    }

    @Test void rejectsAllDynamicStringsBodiesAndObjectsBeforeFormatting() {
        Object dangerous = new Object() { @Override public String toString() { throw new AssertionError("must not stringify"); } };
        String output = encode(event("email={} password={} payload={} otp={} object={}",
                "alice@example.test", "unlabelled-synthetic-secret", "{\"body\":\"private\"}", 123456, dangerous));
        for (String forbidden : new String[]{"alice", "unlabelled", "private", "123456", "synthetic-mdc-secret"})
            assertFalse(output.contains(forbidden), forbidden);
        assertTrue(output.contains("[REDACTED]"));
        assertTrue(output.contains("1".repeat(32))); // IDs belong to dedicated fields, not labels.
    }

    @Test void validUtcJsonSingleLineAndEscapedControlCharacters() {
        String output = encode(event("fixed_event\nsecond_line"));
        var json = JsonMapper.builder().build().readTree(output);
        assertTrue(json.path("timestamp").asString().endsWith("Z"));
        assertEquals("/notes/{id}", json.path("http_route").asString());
        assertEquals("500", json.path("http_status").asString());
        assertEquals(1, output.chars().filter(c -> c == '\n').count());
    }

    @Test void stackFramesAndCausesSurviveWithoutAnyExceptionMessages() {
        var event = event("dependency_failed");
        var root = new IllegalArgumentException("SELECT password='synthetic-query-secret'; code=123456");
        var error = new IllegalStateException("https://example.test/?token=synthetic-secret", root);
        error.addSuppressed(new RuntimeException("alice@example.test"));
        event.setThrowableProxy(new ThrowableProxy(error));
        String output = encode(event);
        assertTrue(output.contains("IllegalStateException"));
        assertTrue(output.contains("IllegalArgumentException"));
        assertTrue(output.contains("Caused by"));
        assertTrue(output.contains("SafeJsonEncoderTest.java"));
        for (String forbidden : new String[]{"synthetic-secret", "synthetic-query", "123456", "alice", "SELECT"})
            assertFalse(output.contains(forbidden), forbidden);
    }

    @Test void libraryFormattedPayloadsAndInvalidMdcAreNeverEmitted() {
        var lib = new LoggingEvent(getClass().getName(),
                (Logger) LoggerFactory.getLogger("org.vendor.http"), Level.ERROR,
                "Authorization: Bearer synthetic-password; body-private", null, null);
        lib.setMDCPropertyMap(Map.of("trace_id", "invalid\nsecret", "request_id", "alice@example.test"));
        String output = encode(lib);
        assertTrue(output.contains("library_event"));
        assertFalse(output.contains("body-private"));
        assertFalse(output.contains("alice"));
        assertFalse(output.contains("trace_id"));
    }

    @Test void literalDefenseAndOutputAreBounded() {
        String output = encode(event("email=alice@example.test cookie=synthetic-cookie "
                + "https://example.test/?code=synthetic-code IP=192.168.1.15 otp=123456 "));
        assertFalse(output.contains("alice"));
        assertFalse(output.contains("synthetic-"));
        assertFalse(output.contains("192.168.1.15"));
        assertFalse(output.contains("123456"));
        assertTrue(encode(event("x".repeat(100000))).length() < 10000);
        assertTrue(encode(event("count={} ok={}", 25, true)).contains("count=25 ok=true"));
    }

    @Test void actorIdIsEmittedOnlyForAnInternalAccountUuid() {
        for (String unsafe : new String[]{"alice@example.test", "11111111222233334444555555555555",
                "11111111-2222-3333-4444-55555555555", "Alice", "1".repeat(40), "Bearer synthetic-token"})
            assertFalse(withMdc("user_id", unsafe).contains("user_id"), unsafe);
        String output = withMdc("user_id", "11111111-2222-3333-4444-555555555555");
        assertTrue(output.contains("\"user_id\":\"11111111-2222-3333-4444-555555555555\""));
    }

    @Test void branchingThrowableGraphHasGlobalBudget() {
        RuntimeException root = new RuntimeException("synthetic-secret");
        RuntimeException cursor = root;
        for (int i = 0; i < 100; i++) {
            var next = new RuntimeException("synthetic-secret");
            cursor.initCause(next);
            for (int j = 0; j < 8; j++) cursor.addSuppressed(new RuntimeException("synthetic-secret"));
            cursor = next;
        }
        var event = event("failure");
        event.setThrowableProxy(new ThrowableProxy(root));
        String output = encode(event);
        assertTrue(output.length() < 60000);
        assertTrue(output.contains("truncated"));
        assertFalse(output.contains("synthetic-secret"));
    }
}
