package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.content.domain.model.Question;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO admin — **khác** public ở chỗ thấy được đáp án đúng của option và `searchKeywords`.
 * Chỉ sinh ra từ endpoint có `@PreAuthorize("hasRole('ADMIN')")`.
 */
public record AdminQuestionDTO(UUID id,
                               UUID moduleId,
                               String title,
                               String answerHtml,
                               String difficulty,
                               List<String> tags,
                               List<String> searchKeywords,
                               int sortOrder,
                               Instant createdAt,
                               Instant updatedAt,
                               List<AdminQuestionOptionDTO> options) {

    /** Option cho admin — có `isCorrect`, thứ mà public DTO tuyệt đối không được chứa. */
    public record AdminQuestionOptionDTO(UUID id, String content, boolean isCorrect, int displayOrder) {
    }

    public static AdminQuestionDTO from(Question question, List<AdminQuestionOptionDTO> options) {
        return new AdminQuestionDTO(question.getId(), question.getModuleId(), question.getTitle(),
                question.getAnswerHtml(), question.getDifficulty().name(), question.getTags(),
                question.getSearchKeywords(), question.getSortOrder(), question.getCreatedAt(),
                question.getUpdatedAt(), options);
    }
}
