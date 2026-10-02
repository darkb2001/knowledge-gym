package com.knowledgegym.blog.domain.port;

import java.util.List;
import java.util.UUID;

/** A text-only provider contract. The caller supplies the entire evidence set; providers have no tools. */
public interface AiWriterPort {
    Completion draft(String topic, String angle, List<Source> sources, String template);
    Completion revise(String title, String body, String instruction, List<Source> sources);

    record Source(UUID id, String title, String summary, String canonicalUrl) {}
    record Completion(String title, String bodyHtml, String excerpt, String seoTitle, String seoDescription,
                      List<String> seoKeywords, List<UUID> sourceIds, String model,
                      int inputTokens, int outputTokens, double costUsd) {
        public Completion { seoKeywords = seoKeywords == null ? List.of() : List.copyOf(seoKeywords); sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds); }
        public int tokensUsed() { return Math.max(0, inputTokens) + Math.max(0, outputTokens); }
    }
}
