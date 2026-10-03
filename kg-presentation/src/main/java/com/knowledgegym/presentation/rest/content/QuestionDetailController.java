package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.GetQuestionDetailUseCase;
import com.knowledgegym.presentation.rest.content.dto.QuestionDetailDTO;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Chi tiết 1 câu hỏi — tách khỏi {@link QuestionController} vì có cache.
 *
 * `@Cacheable` đặt ở controller method (không phải use case trong kg-core) để kg-core
 * vẫn là Java thuần, không kéo Spring Cache vào domain/application.
 */
@RestController
@RequestMapping("/questions")
public class QuestionDetailController {

    private final GetQuestionDetailUseCase getQuestionDetailUseCase;

    public QuestionDetailController(GetQuestionDetailUseCase getQuestionDetailUseCase) {
        this.getQuestionDetailUseCase = getQuestionDetailUseCase;
    }

    @GetMapping("/{id}")
    @Cacheable(value = "questions", key = "#id")
    public QuestionDetailDTO detail(@PathVariable UUID id) {
        var detail = getQuestionDetailUseCase.execute(id);
        if (detail.question().getContentStatus() != com.knowledgegym.content.domain.model.Question.ContentStatus.PUBLISHED) {
            throw new NotFoundException("Câu hỏi không tồn tại");
        }
        return QuestionDetailDTO.from(detail);
    }
}
