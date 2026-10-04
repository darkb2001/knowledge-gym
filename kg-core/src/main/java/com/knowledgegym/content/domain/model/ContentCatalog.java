package com.knowledgegym.content.domain.model;

import java.util.List;

/**
 * Toàn bộ nội dung đọc được từ docs/ — output của {@code ContentSource} port.
 * Adapter (Jsoup) chịu trách nhiệm parse; core chỉ làm việc với shape này.
 */
public record ContentCatalog(List<ContentTrack> tracks,
                             List<Topic> topics,
                             List<ModuleRef> modules,
                             List<ParsedQuestion> questions) {

    /** Catalog không kèm track (caller cũ) — coi như docs chưa khai báo track nào. */
    public ContentCatalog(List<Topic> topics, List<ModuleRef> modules, List<ParsedQuestion> questions) {
        this(List.of(), topics, modules, questions);
    }

    public int questionCount() {
        return questions.size();
    }
}
