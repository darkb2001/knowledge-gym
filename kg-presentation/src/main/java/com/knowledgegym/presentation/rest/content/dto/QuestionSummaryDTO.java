package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.content.domain.model.Question;

import java.util.List;
import java.util.UUID;

/**
 * DTO cho list/search — **không** chứa `answerHtml` (danh sách không cần payload nặng)
 * và không chứa bất kỳ thông tin đáp án quiz nào.
 */
public record QuestionSummaryDTO(UUID id,
                                 UUID moduleId,
                                 String moduleSlug,
                                 String title,
                                 String difficulty,
                                 List<String> tags,
                                 int sortOrder) {

    public static QuestionSummaryDTO of(Question question, String moduleSlug) {
        return new QuestionSummaryDTO(question.getId(), question.getModuleId(), moduleSlug,
                question.getTitle(), question.getDifficulty().name(), question.getTags(),
                question.getSortOrder());
    }
}
