package com.knowledgegym.agent;

import com.knowledgegym.blog.application.GenerateBlogUseCase;
import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import com.knowledgegym.blog.domain.port.BlogWriterRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** One writer tick — shared by embedded scheduler and POST /internal/writer. */
@Component
@ConditionalOnProperty(name = "app.blog.writer.enabled", havingValue = "true")
public class WriterCronRunner {
    private static final Logger log = LoggerFactory.getLogger(WriterCronRunner.class);
    private final BlogScheduleSettingsPort settings;
    private final BlogWriterRepository writerRepository;
    private final GenerateBlogUseCase generate;
    private final int requestLimit;
    private final int tokenLimit;
    private final double costLimit;
    private final boolean apiKeyConfigured;

    public WriterCronRunner(BlogScheduleSettingsPort settings, BlogWriterRepository writerRepository,
                            GenerateBlogUseCase generate,
                            @Value("${app.blog.writer.daily-request-limit:20}") int requestLimit,
                            @Value("${app.blog.writer.daily-token-limit:80000}") int tokenLimit,
                            @Value("${app.blog.writer.daily-cost-limit-usd:10}") double costLimit,
                            @Value("${app.blog.writer.api-key:}") String apiKey) {
        this.settings = settings;
        this.writerRepository = writerRepository;
        this.generate = generate;
        this.requestLimit = requestLimit;
        this.tokenLimit = tokenLimit;
        this.costLimit = costLimit;
        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
    }

    public void reclaimStale() {
        settings.reclaimStaleGenerating(Duration.ofMinutes(15));
    }

    public Result runOnce() {
        if (!apiKeyConfigured) {
            return Result.skipped("Writer API key is not configured");
        }
        var config = settings.current();
        var now = ZonedDateTime.now(config.timezone());
        LocalDate today = now.toLocalDate();
        if (config.enabled() && !now.toLocalTime().isBefore(config.localTime())
                && !today.equals(config.lastScheduledDate())) {
            settings.enqueueScheduled(today, Instant.now(), config.dailyLimit());
        }
        if (!settings.withinGlobalLimits(requestLimit, tokenLimit, costLimit)) {
            return Result.skipped("Writer daily budget exhausted");
        }
        var job = settings.claimNext();
        if (job == null) {
            return Result.skipped("No writer job queued");
        }
        if (job.generatedPostId() != null) {
            UUID run = writerRepository.startRun(job.id(),
                    "recover generated_post_id=" + job.generatedPostId());
            settings.complete(job.id(), run, job.generatedPostId(), 0, 0,
                    "Recovered after restart; post already created");
            return Result.completed("Recovered existing draft for job " + job.id());
        }
        var run = writerRepository.startRun(job.id(),
                "topic=" + job.topic() + ", idempotency=" + job.idempotencyKey());
        if (!settings.reserveGlobalBudget(run, requestLimit, tokenLimit, costLimit, 8_000, 0.005d)) {
            settings.deferForBudget(job.id(), run);
            return Result.skipped("Writer budget reservation failed");
        }
        try {
            var result = generate.generate(job, run);
            settings.complete(job.id(), run, result.post().id(), result.tokensUsed(), result.costUsd(),
                    "quality=" + result.qualityScore() + ", template=" + result.template());
            log.info("AI writer completed job {} with status {} and quality {}",
                    job.id(), result.post().status(), result.qualityScore());
            return Result.completed("Completed job " + job.id());
        } catch (Exception failure) {
            settings.releaseUnsettledBudget(run);
            settings.fail(job.id(), run,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
            log.warn("AI writer job {} failed; queued for bounded retry or review", job.id(), failure);
            return Result.failed("Writer job failed: " + failure.getMessage());
        }
    }

    public record Result(String status, String message) {
        static Result completed(String message) { return new Result("COMPLETED", message); }
        static Result skipped(String message) { return new Result("SKIPPED", message); }
        static Result failed(String message) { return new Result("FAILED", message); }
    }
}
