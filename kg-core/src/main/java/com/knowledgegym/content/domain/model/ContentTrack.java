package com.knowledgegym.content.domain.model;

/**
 * Track = lộ trình lớn chứa nhiều topic (Java backend, AWS DVA…).
 *
 * <p>`slug` là natural key, khai báo trong `docs/index.html` bằng các attribute trên
 * `div.nav-group`: `data-track`, `data-track-name`, `data-track-desc`, `data-track-icon`.
 */
public class ContentTrack {

    private String slug;
    private String name;
    private String description;
    private String icon;
    private int displayOrder;

    public ContentTrack(String name, String slug, int displayOrder) {
        this(name, slug, displayOrder, null, null);
    }

    public ContentTrack(String name, String slug, int displayOrder, String description, String icon) {
        this.name = name;
        this.slug = slug;
        this.displayOrder = displayOrder;
        this.description = description;
        this.icon = icon;
    }

    /** Track chỉ có slug (docs không khai báo tên) — suy tên từ slug để UI không hiện slug thô. */
    public static ContentTrack fromSlug(String slug, int displayOrder) {
        StringBuilder name = new StringBuilder();
        for (String part : slug.split("-")) {
            if (part.isBlank()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return new ContentTrack(name.isEmpty() ? slug : name.toString(), slug, displayOrder);
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
