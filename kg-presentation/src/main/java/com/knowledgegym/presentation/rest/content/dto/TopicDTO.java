package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.content.domain.model.TopicWithStats;

import java.util.UUID;

public record TopicDTO(UUID id, String slug, String name, String description,
                       int displayOrder, long moduleCount) {

    public static TopicDTO from(TopicWithStats stats) {
        var topic = stats.topic();
        return new TopicDTO(topic.getId(), topic.getSlug(), topic.getName(), topic.getDescription(),
                topic.getDisplayOrder(), stats.moduleCount());
    }
}
