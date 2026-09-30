package com.knowledgegym.content.domain.model;

import java.util.UUID;

import com.knowledgegym.shared.domain.model.Difficulty;

/**
 * Tiêu chí tìm kiếm câu hỏi — shape trung lập, không dùng Spring Data `Pageable`.
 * `q` (full-text) đi qua PostgreSQL tsvector; các filter còn lại qua cột/index thường.
 *
 * Record này **không** tự chuẩn hoá `q`: việc bỏ dấu + loại hư từ là tri thức về nội dung, thuộc
 * application layer ({@code QueryQuestionsUseCase} gọi {@code SearchText.normalizeQuery}). Nếu
 * chuẩn hoá ở đây thì domain model phải import application — sai chiều phụ thuộc Clean Architecture.
 *
 * `page` 1-based (khác Spring Data 0-based) và bị chặn ở {@link #MAX_PAGE}: offset truyền cho
 * PostgreSQL là `(page-1) * size`, nhân hai số khai báo `int` sẽ tràn thành số âm khi page cực
 * lớn → Postgres ném lỗi và API trả lỗi không liên quan tới tham số client gửi.
 *
 * @param page 1-based, đã clamp
 */
public record QuestionQuery(UUID moduleId,
                            Difficulty difficulty,
                            String tag,
                            String q,
                            int page,
                            int size) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    /**
     * Trần page. `Integer.MAX_VALUE` là con số client có thể gửi (đã thấy thật: 409 kèm message
     * "Resource already exists"). Chặn ở ngưỡng đủ lớn để không giới hạn dữ liệu thật
     * (10^7 trang × 100 = 10^9 bản ghi) nhưng vẫn giữ `(page-1)*size` trong biên `int`.
     */
    public static final int MAX_PAGE = 10_000_000;

    public QuestionQuery {
        if (page < 1) page = 1;
        if (page > MAX_PAGE) page = MAX_PAGE;
        if (size < 1) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
        if (tag != null && tag.isBlank()) tag = null;
        if (q != null && q.isBlank()) q = null;
    }

    /** Bản sao đã chuẩn hoá `q` — dùng bởi application layer sau khi áp {@code SearchText}. */
    public QuestionQuery withQuery(String normalizedQ) {
        return new QuestionQuery(moduleId, difficulty, tag, normalizedQ, page, size);
    }

    public boolean hasFullText() {
        return q != null;
    }

    /** Offset dạng `long` — xem {@link #MAX_PAGE} về lý do không dùng `int`. */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
