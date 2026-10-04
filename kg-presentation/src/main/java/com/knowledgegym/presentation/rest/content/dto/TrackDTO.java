package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.content.domain.model.ContentTrack;

public record TrackDTO(String slug, String name, String description, String icon, int displayOrder) {

    public static TrackDTO from(ContentTrack track) {
        return new TrackDTO(track.getSlug(), track.getName(), track.getDescription(),
                track.getIcon(), track.getDisplayOrder());
    }
}
