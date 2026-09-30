package com.knowledgegym.content.application;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.shared.domain.model.PageResult;

/**
 * Đọc câu hỏi cho API public. Chi tiết 1 câu đi qua `QuestionRepository.findById`.
 *
 * `q` được chuẩn hoá ở đây (bỏ dấu + loại hư từ) trước khi xuống repository, vì index
 * (`searchable_text`, xem `SearchText`) và truy vấn phải dùng **cùng** không gian token — nếu
 * không, người gõ "không đồng bộ" sẽ ra 0 hit dù dữ liệu có. Đặt ở application layer (không phải
 * `QuestionQuery` trong domain) để domain model không phải import application.
 */
public class QueryQuestionsUseCase {

    private final QuestionRepository questionRepository;

    public QueryQuestionsUseCase(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public PageResult<Question> execute(QuestionQuery query) {
        String normalized = SearchText.normalizeQuery(query.q());
        QuestionQuery effective = query.withQuery(normalized.isEmpty() ? null : normalized);
        return questionRepository.search(effective);
    }
}
