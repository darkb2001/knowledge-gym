package com.knowledgegym.content.domain.model;

import java.util.List;

/** Topic kèm số module — dùng cho `GET /topics` (UI cần count để hiển thị badge). */
public record TopicWithStats(Topic topic, long moduleCount) {

    public static TopicWithStats of(Topic topic, List<?> modules) {
        return new TopicWithStats(topic, modules.size());
    }
}
