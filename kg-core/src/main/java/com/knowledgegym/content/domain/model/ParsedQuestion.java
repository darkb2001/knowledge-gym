package com.knowledgegym.content.domain.model;

import com.knowledgegym.shared.domain.model.Difficulty;

import java.util.List;

/**
 * Câu hỏi vừa parse từ HTML — chưa persist (chưa có id, module chỉ được tham chiếu bằng slug).
 *
 * `sortOrder` = số thứ tự `.qa-card` trong file module (1..N) và là một nửa của natural key
 * `(module_id, sort_order)` dùng cho upsert idempotent — xem `V014__content_import_support.sql`.
 *
 * `answerHtml` đã được sanitize (Jsoup Safelist) trước khi tới đây.
 * `searchKeywords` đi vào cột `questions.searchable_text` (xem `V015`).
 */
public record ParsedQuestion(String moduleSlug,
                             String title,
                             String answerHtml,
                             List<String> tags,
                             List<String> searchKeywords,
                             int sortOrder,
                             Difficulty difficulty) {
}
