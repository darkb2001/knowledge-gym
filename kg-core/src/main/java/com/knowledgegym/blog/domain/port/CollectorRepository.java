package com.knowledgegym.blog.domain.port;

import com.knowledgegym.blog.domain.model.CollectedItem;
import com.knowledgegym.blog.domain.model.CollectorSource;
import java.util.List;
import java.util.UUID;

public interface CollectorRepository {
    List<CollectorSource> findDueSources();
    default List<CollectorSource> findDueSourcesForDomains(List<String> domains) { return findDueSources(); }
    int saveCollected(UUID sourceId, List<CollectedItem> items);
    void markFetched(UUID sourceId);
    void scoreItem(UUID itemId);
    UUID startRun(String inputSummary);
    void finishRun(UUID runId, boolean succeeded, String outputSummary, String errorMessage);
}
