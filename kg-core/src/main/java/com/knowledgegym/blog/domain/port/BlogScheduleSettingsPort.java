package com.knowledgegym.blog.domain.port;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public interface BlogScheduleSettingsPort {
    Settings current();
    Settings update(boolean enabled, LocalTime localTime, ZoneId zone, int dailyLimit,
                    PublishPolicy policy, int qualityThreshold, UUID actorId);
    boolean enqueueScheduled(LocalDate localDate, Instant scheduledAt, int dailyLimit);
    UUID enqueueNow(String topic, UUID actorId, String requestId);
    Job findJob(UUID queueId);
    Job claimNext();
    /** Requeue/complete GENERATING rows left behind by a crash so the worker is restart-safe. */
    int reclaimStaleGenerating(java.time.Duration staleAfter);
    void complete(UUID queueId, UUID runId, UUID postId, int tokens, double costUsd, String output);
    void fail(UUID queueId, UUID runId, String error);
    void deferForBudget(UUID queueId,UUID runId);
    boolean withinGlobalLimits(int todayRequestLimit, int todayTokenLimit, double todayCostLimitUsd);
    boolean reserveGlobalBudget(UUID requestId,int requestLimit,int tokenLimit,double costLimitUsd,int reservedTokens,double reservedCostUsd);
    void settleGlobalBudget(UUID requestId,int actualTokens,double actualCostUsd);
    /** Release an unused reservation after provider/resilience failure (no model response). */
    void releaseUnsettledBudget(UUID requestId);
    boolean publishIfStillQualified(UUID postId,int actualQualityScore);

    enum PublishPolicy { MANUAL_REVIEW, AUTO_PUBLISH_QUALIFIED }
    record Settings(boolean enabled, LocalTime localTime, ZoneId timezone, int dailyLimit,
                    PublishPolicy policy, int qualityThreshold, LocalDate lastScheduledDate,
                    Instant lastRunAt) {}
    record Job(UUID id, String topic, String angle, String idempotencyKey, UUID requestedBy,
               String status, int attempts, String errorMessage, UUID generatedPostId) {}
}
