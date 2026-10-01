package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.IntStream;

@Repository
public class QuestionOptionRepositoryAdapter implements QuestionOptionRepository {
    private final JdbcTemplate db;

    public QuestionOptionRepositoryAdapter(JdbcTemplate db) { this.db = db; }

    private static QuestionOption map(ResultSet rs) throws SQLException {
        var option = new QuestionOption(rs.getString("content"), rs.getBoolean("is_correct"),
                rs.getInt("display_order"));
        option.setId(rs.getObject("id", UUID.class));
        option.setQuestionId(rs.getObject("question_id", UUID.class));
        return option;
    }

    @Override
    public List<QuestionOption> findByQuestionId(UUID id) {
        return db.query("SELECT * FROM question_options WHERE question_id = ? ORDER BY display_order",
                (rs, row) -> map(rs), id);
    }

    @Override
    public Map<UUID, List<QuestionOption>> findByQuestionIds(Collection<UUID> ids) {
        Map<UUID, List<QuestionOption>> result = new LinkedHashMap<>();
        if (ids.isEmpty()) return result;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        db.query("SELECT * FROM question_options WHERE question_id IN (" + placeholders + ") ORDER BY display_order",
                rs -> {
                    var option = map(rs);
                    result.computeIfAbsent(option.getQuestionId(), key -> new ArrayList<>()).add(option);
                }, ids.toArray());
        return result;
    }

    @Override
    @Transactional
    public int replaceOptions(UUID questionId, List<QuestionOption> options) {
        // Concurrent import/backfill must not interleave deletion and insertion.
        db.queryForObject("SELECT id FROM questions WHERE id = ? FOR UPDATE", UUID.class, questionId);
        var previous = findByQuestionId(questionId);
        boolean unchanged = previous.size() == options.size() && IntStream.range(0, previous.size())
                .allMatch(index -> previous.get(index).getContent().equals(options.get(index).getContent())
                        && previous.get(index).isCorrect() == options.get(index).isCorrect()
                        && previous.get(index).getDisplayOrder() == options.get(index).getDisplayOrder());
        // Keep IDs referenced by existing quiz answers and by browsers with an open quiz.
        if (unchanged) return options.size();
        db.update("DELETE FROM question_options WHERE question_id = ?", questionId);
        for (var option : options) {
            db.update("INSERT INTO question_options(id, question_id, content, is_correct, display_order) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), questionId, option.getContent(), option.isCorrect(), option.getDisplayOrder());
        }
        return options.size();
    }
}
