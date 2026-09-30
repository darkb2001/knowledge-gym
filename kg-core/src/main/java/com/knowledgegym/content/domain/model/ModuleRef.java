package com.knowledgegym.content.domain.model;

import java.util.UUID;

/**
 * Module = 1 file HTML trong docs/. `slug` là natural key (từ tên file, vd `05-database`).
 * Đặt tên ModuleRef để không lẫn với `java.lang.Module` và với module Gradle.
 */
public class ModuleRef {

    private UUID id;
    private UUID topicId;
    private String name;
    private String slug;
    private String description;
    private int displayOrder;
    private boolean active = true;

    /**
     * Hint khi parse (`nav-group` trong index.html) — KHÔNG phải cột DB.
     * Import resolve hint này thành `topicId` trước khi persist.
     */
    private String topicSlug;

    public ModuleRef(String name, String slug, int displayOrder) {
        this.name = name;
        this.slug = slug;
        this.displayOrder = displayOrder;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTopicId() { return topicId; }
    public void setTopicId(UUID topicId) { this.topicId = topicId; }
    public String getTopicSlug() { return topicSlug; }
    public void setTopicSlug(String topicSlug) { this.topicSlug = topicSlug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
