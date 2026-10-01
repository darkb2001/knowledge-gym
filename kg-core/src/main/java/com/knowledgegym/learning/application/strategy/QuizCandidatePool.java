package com.knowledgegym.learning.application.strategy;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.Difficulty;
import com.knowledgegym.shared.domain.model.PageResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Đọc pool câu hỏi của module cho các strategy — dùng chung để 4 strategy không mỗi cái tự paged
 * query một kiểu (và tự quên phân trang ở chỗ khác nhau).
 *
 * <p>Không phải Spring bean: strategy nhận nó qua constructor, `UseCaseConfig` tạo như một helper.
 */
public final class QuizCandidatePool {

    /**
     * Trần số câu đọc về để xếp hạng. Quiz tối đa 50 câu nhưng WEAKNESS/SPACED cần đối chiếu trên
     * toàn module rồi mới cắt, nên không thể chỉ lấy `count` câu đầu. 500 đủ rộng cho module thật
     * (lớn nhất trong docs/ ~ vài chục câu) và chặn trường hợp dữ liệu phình bất thường.
     */
    private static final int MAX_POOL = 500;

    private final QuestionRepository questionRepository;
    private final ModuleRepository moduleRepository;

    public QuizCandidatePool(QuestionRepository questionRepository, ModuleRepository moduleRepository) {
        this.questionRepository = questionRepository;
        this.moduleRepository = moduleRepository;
    }

    /** Module tồn tại hay không — use case cần trả 404 trước khi sinh quiz. */
    public ModuleRef requireModule(UUID moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module không tồn tại: " + moduleId));
    }

    /**
     * Toàn bộ câu của module (đã áp filter `difficulty`), theo `sort_order`.
     *
     * <p>Lặp theo {@code MAX_SIZE} của port thay vì thêm method mới: module > 100 câu vẫn lấy đủ,
     * không bị cắt im lặng (cùng lý do/commented ở {@code EnrollCardsUseCase.questionsOfModule}).
     */
    public List<Question> questionsOf(UUID moduleId, Difficulty difficulty) {
        requireModule(moduleId);
        List<Question> all = new ArrayList<>();
        int page = 1;
        while (all.size() < MAX_POOL) {
            PageResult<Question> result = questionRepository.search(
                    new QuestionQuery(moduleId, difficulty, null, null, page, QuestionQuery.MAX_SIZE));
            all.addAll(result.items());
            if (result.items().size() < QuestionQuery.MAX_SIZE) {
                break;
            }
            page++;
        }
        return all.size() > MAX_POOL ? List.copyOf(all.subList(0, MAX_POOL)) : all;
    }
}
