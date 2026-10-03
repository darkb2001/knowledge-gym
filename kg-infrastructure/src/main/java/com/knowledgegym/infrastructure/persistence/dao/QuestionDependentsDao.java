package com.knowledgegym.infrastructure.persistence.dao;

import com.knowledgegym.shared.application.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/** Guards legacy hard-delete/import paths. Never delete learning history to satisfy a FK. */
@Component
public class QuestionDependentsDao {
    @PersistenceContext
    private EntityManager entityManager;

    /** Compatibility method name; now validates rather than deleting dependencies. Caller owns the transaction. */
    public void deleteForModule(UUID moduleId, Collection<Integer> keepSortOrders) {
        boolean whole = keepSortOrders == null || keepSortOrders.isEmpty();
        String doomed = "SELECT id FROM questions WHERE module_id = :moduleId"
                + (whole ? "" : " AND sort_order <> ALL(cast(:keepSortOrders as int[]))");
        guard(doomed, "moduleId", moduleId, whole ? null : toPgIntArrayLiteral(keepSortOrders));
    }

    public void deleteForQuestion(UUID questionId) {
        guard("SELECT id FROM questions WHERE id=:id", "id", questionId, null);
    }

    private void guard(String doomed, String parameter, UUID id, String keep) {
        // Serialize with new FK references before checking even ON DELETE CASCADE / SET NULL links.
        bind(entityManager.createNativeQuery(doomed + " FOR UPDATE"), parameter, id, keep).getResultList();
        for (String table : new String[]{"srs_cards", "study_attempts", "quiz_session_questions",
                "interview_answers", "daily_challenge_assignments", "notes", "learning_draft_materializations", "blog_posts"}) {
            String column = table.equals("blog_posts") ? "source_question_id" : "question_id";
            Number count = (Number) bind(entityManager.createNativeQuery(
                    "SELECT count(*) FROM " + table + " WHERE " + column + " IN (" + doomed + ")"), parameter, id, keep).getSingleResult();
            if (count.longValue() > 0) {
                throw new ConflictException("Câu hỏi có dữ liệu học hoặc nội dung tham chiếu. Hãy ẩn câu hỏi thay vì xóa/re-import loại bỏ lịch sử.");
            }
        }
    }

    private static Query bind(Query query, String parameter, UUID id, String keep) {
        query.setParameter(parameter, id);
        if (keep != null) query.setParameter("keepSortOrders", keep);
        return query;
    }

    static String toPgIntArrayLiteral(Collection<Integer> values) {
        return values.stream().map(String::valueOf).filter(value -> value.matches("-?\\d+"))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
