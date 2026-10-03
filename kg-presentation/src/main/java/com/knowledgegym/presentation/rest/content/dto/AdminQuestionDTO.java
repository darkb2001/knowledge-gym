package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionOption;

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
                               String contentStatus,
                               List<AdminQuestionOptionDTO> options) {

    /**
     * Option cho admin — có `isCorrect`, thứ mà public DTO tuyệt đối không được chứa.
     * Nhận thẳng domain `QuestionOption` để controller không phải tự map và không quên field nào.
     */
    public record AdminQuestionOptionDTO(UUID id, String content, boolean isCorrect, int displayOrder) {

        public static AdminQuestionOptionDTO of(QuestionOption option) {
            return new AdminQuestionOptionDTO(option.getId(), option.getContent(), option.isCorrect(),
                    option.getDisplayOrder());
        }
    }

    public static AdminQuestionDTO from(Question question, List<QuestionOption> options) {
        return new AdminQuestionDTO(question.getId(), question.getModuleId(), question.getTitle(),
                question.getAnswerHtml(), question.getDifficulty().name(), question.getTags(),
                question.getSearchKeywords(), question.getSortOrder(), question.getCreatedAt(),
                question.getUpdatedAt(), question.getContentStatus().name(),
                options == null ? List.of() : options.stream().map(AdminQuestionOptionDTO::of).toList());
    }
}
