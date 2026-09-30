package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.application.CatalogQueryUseCase;
import com.knowledgegym.content.application.QueryQuestionsUseCase;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.presentation.rest.content.dto.PageResponse;
import com.knowledgegym.presentation.rest.content.dto.QuestionSummaryDTO;
import com.knowledgegym.shared.domain.model.Difficulty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Danh sách/tìm kiếm câu hỏi cho người học (public, cần đăng nhập theo SecurityConfig). */
@RestController
@RequestMapping("/questions")
public class QuestionController {

    private final QueryQuestionsUseCase queryQuestionsUseCase;
    private final CatalogQueryUseCase catalogQueryUseCase;

    public QuestionController(QueryQuestionsUseCase queryQuestionsUseCase,
                              CatalogQueryUseCase catalogQueryUseCase) {
        this.queryQuestionsUseCase = queryQuestionsUseCase;
        this.catalogQueryUseCase = catalogQueryUseCase;
    }

    @GetMapping
    public PageResponse<QuestionSummaryDTO> list(
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        QuestionQuery query = new QuestionQuery(moduleId, parseDifficulty(difficulty), tag, q, page, size);
        var moduleSlugs = catalogQueryUseCase.moduleSlugIndex();
        return PageResponse.of(queryQuestionsUseCase.execute(query),
                question -> QuestionSummaryDTO.of(question, moduleSlugs.get(question.getModuleId())));
    }

    /** Chấp nhận `mid`/`MID`; giá trị lạ → 400 thay vì im lặng bỏ filter. */
    private static Difficulty parseDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("difficulty không hợp lệ: " + raw);
        }
    }
}
