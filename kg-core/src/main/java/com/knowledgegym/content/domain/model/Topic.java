package com.knowledgegym.content.domain.model;

import java.util.UUID;

/** Topic = nhóm module (Foundation / Backend / Systems / Domain & Craft / Roadmap). */
public class Topic {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private int displayOrder;
    private boolean active = true;

    /** Track (lộ trình) chứa topic — suy từ `data-track` của nav-group khi import docs. */
    private String trackSlug;

    public Topic(String name, String slug, int displayOrder) {
        this.name = name;
        this.slug = slug;
        this.displayOrder = displayOrder;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
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
    public String getTrackSlug() { return trackSlug; }
    public void setTrackSlug(String trackSlug) { this.trackSlug = trackSlug; }
}
