package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
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
        lock(questionId);
        if (isManual(questionId)) return findByQuestionId(questionId).size();
        var previous = findByQuestionId(questionId);
        boolean unchanged = previous.size() == options.size() && IntStream.range(0, previous.size())
                .allMatch(index -> previous.get(index).getContent().equals(options.get(index).getContent())
                        && previous.get(index).isCorrect() == options.get(index).isCorrect()
                        && previous.get(index).getDisplayOrder() == options.get(index).getDisplayOrder());
        // Keep IDs referenced by existing quiz answers and by browsers with an open quiz.
        if (unchanged) return options.size();
        // Old quiz answers must never lose their selected option IDs.
        for (var old : previous) {
            if (referenced(old.getId())) return previous.size();
        }
        db.update("DELETE FROM question_options WHERE question_id = ?", questionId);
        for (var option : options) {
            db.update("INSERT INTO question_options(id, question_id, content, is_correct, display_order) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), questionId, option.getContent(), option.isCorrect(), option.getDisplayOrder());
        }
        return options.size();
    }

    @Override public boolean isManual(UUID questionId) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT options_manual FROM questions WHERE id = ?", Boolean.class, questionId));
    }

    private void lock(UUID id) {
        if (db.query("SELECT id FROM questions WHERE id = ? FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), id).isEmpty())
            throw new NotFoundException("Câu hỏi không tồn tại: " + id);
    }
    private boolean referenced(UUID id) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM quiz_answers WHERE selected_option_id = ?)", Boolean.class, id);
    }

    @Override @Transactional
    public List<QuestionOption> saveManual(UUID questionId, List<QuestionOption> requested) {
        lock(questionId);
        if (requested == null || requested.size() < 2 || requested.stream().filter(QuestionOption::isCorrect).count() != 1)
            throw new IllegalArgumentException("options cần ít nhất 2 lựa chọn và đúng 1 đáp án đúng");
        Set<UUID> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        var existing = findByQuestionId(questionId);
        Set<UUID> owned = new HashSet<>();
        for (var old : existing) owned.add(old.getId());
        for (var option : requested) {
            if (option.getContent() == null || option.getContent().isBlank() || option.getDisplayOrder() < 0
                    || !orders.add(option.getDisplayOrder())) throw new IllegalArgumentException("content/displayOrder của option không hợp lệ");
            if (option.getId() != null && (!owned.contains(option.getId()) || !ids.add(option.getId())))
                throw new IllegalArgumentException("option id không thuộc câu hỏi hoặc bị lặp");
        }
        for (var old : existing) {
            if (!ids.contains(old.getId()) && referenced(old.getId()))
                throw new ConflictException("Không thể xóa option đã có trong kết quả quiz");
        }
        for (var old : existing) if (!ids.contains(old.getId()))
            db.update("DELETE FROM question_options WHERE id = ?", old.getId());
        for (var option : requested) {
            if (option.getId() != null) db.update("UPDATE question_options SET content=?, is_correct=?, display_order=? WHERE id=?",
                    option.getContent(), option.isCorrect(), option.getDisplayOrder(), option.getId());
            else db.update("INSERT INTO question_options(id,question_id,content,is_correct,display_order) VALUES(?,?,?,?,?)",
                    UUID.randomUUID(), questionId, option.getContent(), option.isCorrect(), option.getDisplayOrder());
        }
        db.update("UPDATE questions SET options_manual = TRUE WHERE id = ?", questionId);
        return findByQuestionId(questionId);
    }
}
