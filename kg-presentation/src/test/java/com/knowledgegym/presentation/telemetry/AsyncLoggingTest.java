package com.knowledgegym.presentation.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class AsyncLoggingTest {
    @Test void stalledConsoleAndFullQueueNeverBlockApplicationAndMdcIsCaptured() throws Exception {
        var context = new LoggerContext();
        context.setMDCAdapter(((LoggerContext) LoggerFactory.getILoggerFactory()).getMDCAdapter());
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        List<ILoggingEvent> delivered = new ArrayList<>();
        var sink = new AppenderBase<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) {
                delivered.add(event);
                if (delivered.size() == 1) {
                    blocked.countDown();
                    try { release.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
            }
        };
        sink.setContext(context); sink.start();
        var async = new AsyncAppender();
        async.setContext(context); async.setQueueSize(4); async.setDiscardingThreshold(0);
        async.setNeverBlock(true); async.setMaxFlushTime(1000); async.addAppender(sink); async.start();
        var logger = context.getLogger("com.knowledgegym.async-canary");
        logger.setAdditive(false); logger.setLevel(Level.INFO); logger.addAppender(async);
        var previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("request_id", "3".repeat(32));
            logger.info("first_event");
            assertTrue(blocked.await(1, TimeUnit.SECONDS));
            MDC.clear();
            // The worker is stalled. A blocking queue implementation would time out.
            CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 25; i++) logger.info("subsequent_event");
            }).get(1, TimeUnit.SECONDS);
            assertEquals(4, async.getNumberOfElementsInQueue());
            release.countDown();
            async.stop();
            assertEquals("3".repeat(32), delivered.getFirst().getMDCPropertyMap().get("request_id"));
            assertEquals(5, delivered.size(), "bounded queue drops overflow, not application work");
        } finally {
            release.countDown(); async.stop(); context.stop();
            if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
    }
}
