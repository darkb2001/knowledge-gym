package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.content.domain.model.ModuleWithStats;

import java.util.UUID;

public record ModuleDTO(UUID id, String slug, String name, String description,
                        int displayOrder, UUID topicId, String topicSlug, long questionCount) {

    public static ModuleDTO from(ModuleWithStats stats) {
        var module = stats.module();
        return new ModuleDTO(module.getId(), module.getSlug(), module.getName(), module.getDescription(),
                module.getDisplayOrder(), module.getTopicId(), stats.topicSlug(), stats.questionCount());
    }
}
