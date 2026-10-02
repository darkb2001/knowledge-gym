package com.knowledgegym.blog.domain.port;

import com.knowledgegym.blog.domain.model.BlogPost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogWriterRepository {
    List<AiWriterPort.Source> selectSources(int limit);
    List<AiWriterPort.Source> findSources(List<UUID> ids);
    BlogPost createReviewDraft(AiWriterPort.Completion completion, List<UUID> validatedSourceIds,
                               int qualityScore, UUID queueId);
    /** Appends the next immutable revision under a post-row lock; returns the new version. */
    int appendRevision(UUID postId, AiWriterPort.Completion completion,
                       List<UUID> sourceIds, String instruction, int score, UUID actorId);
    void restoreRevision(UUID postId, int version);
    List<Revision> revisions(UUID postId);
    List<BlogPost> reviewQueue(int limit);
    void reject(UUID postId);
    Optional<BlogPost> findPost(UUID postId);
    UUID startRun(UUID queueId, String summary);
    UUID startRevisionRun(UUID actorId, String summary);
    void finishRevisionRun(UUID runId, boolean success, int tokensUsed, double costUsd, String error);
    Stats stats();

    record Revision(UUID id, int version, String title, String body, String excerpt,
                    String seoTitle,String seoDescription,List<String> seoKeywords,
                    String instruction, List<UUID> sourceIds, String model,
                    Integer qualityScore, Integer tokensUsed, Double costUsd,
                    java.time.Instant createdAt) {}
    record Stats(long pendingReview,long generatedToday,long tokensToday,double costToday,double costThisMonth,long failedJobs) {}
}
