package com.knowledgegym.content.domain.model;

import java.util.List;

/**
 * Toàn bộ nội dung đọc được từ docs/ — output của {@code ContentSource} port.
 * Adapter (Jsoup) chịu trách nhiệm parse; core chỉ làm việc với shape này.
 */
public record ContentCatalog(List<Topic> topics,
                             List<ModuleRef> modules,
                             List<ParsedQuestion> questions) {

    public int questionCount() {
        return questions.size();
    }
}
