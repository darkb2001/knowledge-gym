package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.shared.application.NotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Chi tiết 1 câu hỏi cho `GET /questions/{id}`.
 *
 * m4a luôn trả `options` rỗng: đáp án quiz do m6 sinh (xem `## Deferred sang m5+` trong plan).
 * Điểm quan trọng là DTO public **không có** cờ đáp án đúng, nên rỗng cũng không lộ gì.
 */
public class GetQuestionDetailUseCase {

    private final QuestionRepository questionRepository;
    private final ModuleRepository moduleRepository;

    public GetQuestionDetailUseCase(QuestionRepository questionRepository,
                                    ModuleRepository moduleRepository) {
        this.questionRepository = questionRepository;
        this.moduleRepository = moduleRepository;
    }

    public record QuestionDetail(Question question, String moduleSlug, List<QuestionOption> options) {}

    public QuestionDetail execute(UUID id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Câu hỏi không tồn tại: " + id));
        String moduleSlug = moduleRepository.findById(question.getModuleId())
                .map(ModuleRef::getSlug)
                .orElse(null);
        return new QuestionDetail(question, moduleSlug, List.of());
    }
}
