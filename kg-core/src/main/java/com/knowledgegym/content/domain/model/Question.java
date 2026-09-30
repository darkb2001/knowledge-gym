package com.knowledgegym.content.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.knowledgegym.shared.domain.model.Difficulty;

/** Câu hỏi đã persist (bảng `questions`). `answerHtml` đã sanitize ở bước import. */
public class Question {

    private UUID id;
    private UUID moduleId;
    private String title;
    private String answerHtml;
    private Difficulty difficulty;
    private List<String> tags = List.of();
    /**
     * Keyword bổ sung cho full-text search (synonym tiếng Việt, viết tắt như "gc", "jvm").
     * Tokenize động từ title/answer không bắt được các trường hợp này — xem V015.
     */
    private List<String> searchKeywords = List.of();
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getModuleId() { return moduleId; }
    public void setModuleId(UUID moduleId) { this.moduleId = moduleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAnswerHtml() { return answerHtml; }
    public void setAnswerHtml(String answerHtml) { this.answerHtml = answerHtml; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags == null ? List.of() : List.copyOf(tags); }
    public List<String> getSearchKeywords() { return searchKeywords; }
    public void setSearchKeywords(List<String> keywords) {
        this.searchKeywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
