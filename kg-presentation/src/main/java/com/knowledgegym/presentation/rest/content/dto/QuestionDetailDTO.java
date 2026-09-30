package com.knowledgegym.presentation.rest.content.dto;

import com.knowledgegym.content.application.GetQuestionDetailUseCase;

import java.util.List;
import java.util.UUID;

/**
 * Chi tiết câu hỏi cho người học. `options` rỗng ở m4a (quiz sinh ở m6) nhưng vẫn khai báo
 * để FE không phải đổi shape sau — và vì DTO này **không có** trường đáp án đúng.
 */
public record QuestionDetailDTO(UUID id,
                                UUID moduleId,
                                String moduleSlug,
                                String title,
                                String answerHtml,
                                String difficulty,
                                List<String> tags,
                                int sortOrder,
                                List<QuestionOptionDTO> options) {

    public static QuestionDetailDTO from(GetQuestionDetailUseCase.QuestionDetail detail) {
        var question = detail.question();
        return new QuestionDetailDTO(question.getId(), question.getModuleId(), detail.moduleSlug(),
                question.getTitle(), question.getAnswerHtml(), question.getDifficulty().name(),
                question.getTags(), question.getSortOrder(),
                detail.options().stream()
                        .map(option -> new QuestionOptionDTO(option.getId(), option.getContent(),
                                option.getDisplayOrder()))
                        .toList());
    }
}
