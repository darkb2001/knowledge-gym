package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.CatalogQueryUseCase;
import com.knowledgegym.content.application.QueryQuestionsUseCase;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.presentation.rest.content.dto.AdminQuestionDTO;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.Difficulty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/content/questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionReadController {
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final QueryQuestionsUseCase query;
    private final CatalogQueryUseCase catalog;
    public AdminQuestionReadController(QuestionRepository questions, QuestionOptionRepository options,
                                       QueryQuestionsUseCase query, CatalogQueryUseCase catalog) {
        this.questions = questions; this.options = options; this.query = query; this.catalog = catalog;
    }
    public record Summary(UUID id, UUID moduleId, String moduleName, String moduleSlug,
                          String title, String difficulty, java.util.List<String> tags, int sortOrder, int optionCount, String contentStatus) {}
    @GetMapping
    public PageResponse<Summary> list(@RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) UUID moduleId, @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String tag, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        Difficulty level = null;
        if (difficulty != null && !difficulty.isBlank()) {
            try { level = Difficulty.valueOf(difficulty.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("difficulty không hợp lệ: " + difficulty); }
        }
        var result = query.executeAdmin(new QuestionQuery(moduleId, level, tag, q, page, size, topicId));
        var moduleNames = catalog.listModules(null).stream().collect(java.util.stream.Collectors.toMap(
                m -> m.module().getId(), m -> m.module().getName()));
        var slugs = catalog.moduleSlugIndex();
        var counts = options.findByQuestionIds(result.items().stream().map(com.knowledgegym.content.domain.model.Question::getId).toList());
        return PageResponse.of(result, item -> new Summary(item.getId(), item.getModuleId(),
                moduleNames.get(item.getModuleId()), slugs.get(item.getModuleId()), item.getTitle(),
                item.getDifficulty().name(), item.getTags(), item.getSortOrder(),
                counts.getOrDefault(item.getId(), java.util.List.of()).size(), item.getContentStatus().name()));
    }
    @GetMapping("/{id}")
    public AdminQuestionDTO detail(@PathVariable UUID id) {
        var question = questions.findById(id).orElseThrow(() -> new NotFoundException("Câu hỏi không tồn tại: " + id));
        return AdminQuestionDTO.from(question, options.findByQuestionId(id));
    }
}
