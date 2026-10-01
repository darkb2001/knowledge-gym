package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.progress.domain.port.QuestionModulePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Repository
public class QuestionModuleAdapter implements QuestionModulePort {
    private final JdbcTemplate jdbc;
    public QuestionModuleAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Map<UUID, UUID> moduleByQuestion(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(questionIds.size(), "?"));
        Map<UUID, UUID> result = new LinkedHashMap<>();
        jdbc.query("SELECT id, module_id FROM questions WHERE id IN (" + placeholders + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getObject("id", UUID.class), rs.getObject("module_id", UUID.class)),
                questionIds.toArray());
        return result;
    }
}
