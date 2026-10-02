package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import com.knowledgegym.blog.domain.port.BlogWriterRepository;
import com.knowledgegym.blog.domain.service.QualityScorer;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

public final class BlogWriterAdminUseCase {
    private final BlogScheduleSettingsPort settings;
    private final BlogWriterRepository writer;
    private final QualityScorer scorer;
    private final int requestLimit;
    private final int tokenLimit;
    private final double costLimit;

    public BlogWriterAdminUseCase(BlogScheduleSettingsPort settings, BlogWriterRepository writer,
                                  QualityScorer scorer, int requestLimit, int tokenLimit, double costLimit) {
        this.settings = settings;
        this.writer = writer;
        this.scorer = scorer;
        this.requestLimit = requestLimit;
        this.tokenLimit = tokenLimit;
        this.costLimit = costLimit;
    }

    public BlogScheduleSettingsPort.Settings settings() {
        return settings.current();
    }

    public BlogScheduleSettingsPort.Settings update(boolean enabled, String time, String zone, int dailyLimit,
                                                    BlogScheduleSettingsPort.PublishPolicy policy, int threshold,
                                                    UUID actor) {
        if (dailyLimit < 1 || dailyLimit > 10 || threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("Invalid writer limits");
        }
        LocalTime localTime;
        ZoneId zoneId;
        try {
            localTime = LocalTime.parse(time);
            zoneId = ZoneId.of(zone);
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid local time or timezone");
        }
        return settings.update(enabled, localTime, zoneId, dailyLimit, policy, threshold, actor);
    }

    public UUID generateNow(String topic, UUID actor, String requestId) {
        if (topic != null && topic.length() > 200) throw new IllegalArgumentException("Topic is too long");
        if (requestId == null || !requestId.matches("[A-Za-z0-9:_-]{1,120}")) {
            throw new IllegalArgumentException("Invalid request id");
        }
        if (!settings.withinGlobalLimits(requestLimit, tokenLimit, costLimit)) {
            throw new IllegalStateException("Daily AI writer budget is exhausted");
        }
        return settings.enqueueNow(topic, actor, requestId);
    }

    public BlogScheduleSettingsPort.Job job(UUID id) {
        return settings.findJob(id);
    }

    public BlogWriterRepository.Stats stats() {
        return writer.stats();
    }

    public java.util.List<com.knowledgegym.blog.domain.model.BlogPost> reviewQueue() {
        return writer.reviewQueue(50);
    }

    public com.knowledgegym.blog.domain.model.BlogPost post(UUID id) {
        return writer.findPost(id).orElseThrow(() -> new IllegalArgumentException("Post not found"));
    }

    public java.util.List<BlogWriterRepository.Revision> revisions(UUID postId) {
        return writer.revisions(postId);
    }

    public void restore(UUID postId, int version, UUID actor) {
        var post = writer.findPost(postId).orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!"REVIEW".equals(post.status())) {
            throw new IllegalArgumentException("Only review drafts can be restored");
        }
        var prior = writer.revisions(postId).stream().filter(r -> r.version() == version).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Revision not found"));
        var completion = new com.knowledgegym.blog.domain.port.AiWriterPort.Completion(
                prior.title(), prior.body(), prior.excerpt(), prior.seoTitle(), prior.seoDescription(),
                prior.seoKeywords(), prior.sourceIds(), prior.model(), 0, 0, 0);
        writer.appendRevision(postId, completion, prior.sourceIds(), "Restore revision " + version,
                prior.qualityScore() == null ? 0 : prior.qualityScore(), actor);
    }

    public void manualEdit(UUID postId, UUID actor, String title, String body, String excerpt,
                           BlogPostsUseCase.HtmlSanitizer sanitizer) {
        var post = writer.findPost(postId).orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!"REVIEW".equals(post.status())) {
            throw new IllegalArgumentException("Only review drafts can be edited");
        }
        if (title == null || title.isBlank() || title.length() > 200 || body == null || body.length() > 250_000
                || (excerpt != null && excerpt.length() > 300)) {
            throw new IllegalArgumentException("Invalid edit");
        }
        String safe = sanitizer.sanitize(body);
        if (safe.length() < 100 || !safe.toLowerCase().contains("<h2")) {
            throw new IllegalArgumentException("Content must keep section headings after sanitization");
        }
        var history = writer.revisions(postId);
        var ids = history.isEmpty() ? java.util.List.<UUID>of() : history.getLast().sourceIds();
        var completion = new com.knowledgegym.blog.domain.port.AiWriterPort.Completion(
                title.trim(), safe, excerpt, null, null, java.util.List.of(), ids, "manual-edit", 0, 0, 0);
        writer.appendRevision(postId, completion, ids, "Manual edit",
                scorer.score(title, safe, excerpt, ids.size()), actor);
    }

    public void reject(UUID postId) {
        writer.reject(postId);
    }
}
