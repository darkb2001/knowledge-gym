package com.knowledgegym.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.blog.writer.enabled", havingValue = "true")
public class WriterScheduler {
    private final WriterCronRunner runner;
    private final String cronMode;

    public WriterScheduler(WriterCronRunner runner,
                           @Value("${app.cron.mode:embedded}") String cronMode) {
        this.runner = runner;
        this.cronMode = cronMode;
    }

    @Scheduled(fixedDelayString = "${app.blog.writer.poll-ms:30000}",
            initialDelayString = "${app.blog.writer.initial-delay-ms:20000}")
    public void tick() {
        runner.reclaimStale();
        if ("external".equalsIgnoreCase(cronMode)) return;
        runner.runOnce();
    }
}
