package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.port.AiWriterPort;
import com.knowledgegym.blog.domain.port.BlogWriterRepository;
import com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort;
import com.knowledgegym.blog.domain.service.QualityScorer;

import java.util.UUID;

public final class ReviseBlogDraftUseCase {
    private final AiWriterPort writer;
    private final BlogWriterRepository repository;
    private final BlogPostsUseCase.HtmlSanitizer sanitizer;
    private final QualityScorer scorer;
    private final BlogScheduleSettingsPort settings;
    private final int requestLimit;
    private final int tokenLimit;
    private final double costLimit;

    public ReviseBlogDraftUseCase(AiWriterPort writer, BlogWriterRepository repository,
                                  BlogPostsUseCase.HtmlSanitizer sanitizer, QualityScorer scorer,
                                  BlogScheduleSettingsPort settings, int requestLimit, int tokenLimit,
                                  double costLimit) {
        this.writer = writer;
        this.repository = repository;
        this.sanitizer = sanitizer;
        this.scorer = scorer;
        this.settings = settings;
        this.requestLimit = requestLimit;
        this.tokenLimit = tokenLimit;
        this.costLimit = costLimit;
    }

    public BlogWriterRepository.Revision revise(UUID postId, UUID actorId, String instruction) {
        if (instruction == null || instruction.isBlank() || instruction.length() > 2_000) {
            throw new IllegalArgumentException("Revision instruction must be 1–2000 characters");
        }
        var post = repository.findPost(postId).orElseThrow(() -> new IllegalArgumentException("Blog draft not found"));
        if (!"REVIEW".equals(post.status())) {
            throw new IllegalArgumentException("Only review drafts can be revised");
        }
        var history = repository.revisions(postId);
        var sourceIds = history.isEmpty() ? java.util.List.<UUID>of() : history.getLast().sourceIds();
        var sources = repository.findSources(sourceIds);
        if (sources.size() != sourceIds.size()) {
            throw new IllegalStateException("Draft source evidence is no longer available");
        }
        if (!settings.withinGlobalLimits(requestLimit, tokenLimit, costLimit)) {
            throw new IllegalStateException("Daily AI writer budget is exhausted");
        }
        UUID runId = repository.startRevisionRun(actorId, "revision post=" + postId);
        if (!settings.reserveGlobalBudget(runId, requestLimit, tokenLimit, costLimit, 16_000, 0.01d)) {
            repository.finishRevisionRun(runId, false, 0, 0, "Daily AI writer budget is exhausted");
            throw new IllegalStateException("Daily AI writer budget is exhausted");
        }
        int settledTokens = 0;
        double settledCost = 0;
        boolean settled = false;
        try {
            var completion = writer.revise(post.title(), post.body(), instruction, sources);
            settings.settleGlobalBudget(runId, completion.tokensUsed(), completion.costUsd());
            settledTokens = completion.tokensUsed();
            settledCost = completion.costUsd();
            settled = true;
            var allowed = sourceIds.stream().collect(java.util.stream.Collectors.toSet());
            if (completion.sourceIds().isEmpty()
                    || completion.sourceIds().stream().anyMatch(id -> !allowed.contains(id))) {
                throw new IllegalArgumentException("AI revision returned ungrounded source IDs");
            }
            String body = sanitizer.sanitizeGenerated(completion.bodyHtml());
            if (body.length() < 300 || !body.toLowerCase().contains("<h2")) {
                throw new IllegalArgumentException("Revised content failed required checks");
            }
            String title = clean(completion.title(), 200);
            String excerpt = clean(completion.excerpt(), 300);
            if (title.length() < 8) throw new IllegalArgumentException("Revised title failed required checks");
            int score = scorer.score(title, body, excerpt, completion.sourceIds().size());
            StringBuilder sourceLinks = new StringBuilder("<h2>Sources</h2><ul>");
            for (var source : sources) {
                if (completion.sourceIds().contains(source.id()) && source.canonicalUrl().startsWith("https://")) {
                    sourceLinks.append("<li><a href=\"")
                            .append(source.canonicalUrl().replace("&", "&amp;").replace("\"", "&quot;"))
                            .append("\" rel=\"nofollow noopener\">")
                            .append(source.title().replace("<", "&lt;").replace(">", "&gt;"))
                            .append("</a></li>");
                }
            }
            sourceLinks.append("</ul>");
            body = sanitizer.sanitize(body + sourceLinks);
            var safe = new AiWriterPort.Completion(title, body, excerpt,
                    clean(completion.seoTitle(), 200), clean(completion.seoDescription(), 300),
                    completion.seoKeywords().stream().filter(s -> s != null && !s.isBlank()).limit(15).toList(),
                    completion.sourceIds().stream().distinct().toList(), completion.model(),
                    completion.inputTokens(), completion.outputTokens(), completion.costUsd());
            repository.appendRevision(postId, safe, safe.sourceIds(), instruction, score, actorId);
            repository.finishRevisionRun(runId, true, safe.tokensUsed(), safe.costUsd(), null);
            return repository.revisions(postId).getLast();
        } catch (RuntimeException e) {
            if (!settled) settings.releaseUnsettledBudget(runId);
            repository.finishRevisionRun(runId, false, settledTokens, settledCost, e.getMessage());
            throw e;
        }
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String s = value.trim();
        return s.substring(0, Math.min(max, s.length()));
    }
}
