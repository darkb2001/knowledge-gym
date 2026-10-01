package com.knowledgegym.infrastructure.persistence.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xoá các bảng tham chiếu `questions(id)` bằng `NO ACTION` (V004) trước khi xoá câu hỏi.
 *
 * <p>Lý do tồn tại: `srs_cards.question_id` và `study_attempts.question_id` được khai báo
 * `NOT NULL REFERENCES questions(id)` **không** kèm `ON DELETE`, nên Postgres chặn xoá câu hỏi
 * còn thẻ/attempt trỏ tới. Trước m5 hai bảng này luôn rỗng nên re-import xoá câu vô hại; m5 là
 * phase đầu tiên ghi row vào đó, biến re-import (và `DELETE /admin/content/questions/{id}`) thành
 * lỗi FK 500 ngay khi có người đã enroll. QueryDueUseCase đã chịu được thẻ mồ côi, nên xoá thẻ
 * theo câu bị bỏ là hành vi đúng: câu không còn thì thẻ ôn nó cũng vô nghĩa.
 *
 * <p>Native SQL vì cần `DELETE ... WHERE question_id IN (SELECT ... FROM questions ...)` — cùng
 * một tiêu chí chọn câu mà `deleteAbsentSortOrders` sắp xoá. `keepSortOrders` truyền dạng literal
 * `{1,2,3}` rồi cast `int[]`: bind một `Collection` vào native query phụ thuộc hành vi mở rộng
 * tham số của Hibernate, còn literal thì không (đúng cách `QuestionRepositoryAdapter` xử lý `tags`).
 *
 * <p>m6 xoá toàn bộ quiz session chứa câu bị xoá để score/total/membership không lệch nhau.
 * Interview chỉ xoá answer; session giữ nguyên và điểm đã chốt là lịch sử tại thời điểm finish.
 * Daily assignment cũng được dọn để re-import không bị chặn bởi FK.
 */
@Component
public class QuestionDependentsDao {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Xoá thẻ SRS + attempt của mọi câu trong module sẽ bị xoá theo cùng tiêu chí
     * {@code keepSortOrders} của {@code deleteAbsentSortOrders}: rỗng/null → cả module, ngược lại
     * chỉ những câu có `sort_order` không được giữ.
     */
    public void deleteForModule(UUID moduleId, Collection<Integer> keepSortOrders) {
        boolean wholeModule = keepSortOrders == null || keepSortOrders.isEmpty();
        String doomed = wholeModule
                ? "SELECT id FROM questions WHERE module_id = :moduleId"
                : "SELECT id FROM questions WHERE module_id = :moduleId "
                        + "AND sort_order <> ALL(cast(:keepSortOrders as int[]))";
        String keepLiteral = wholeModule ? null : toPgIntArrayLiteral(keepSortOrders);

        for (String table : new String[]{"quiz_sessions", "interview_answers", "daily_challenge_assignments", "study_attempts", "srs_cards"}) {
            String sql = table.equals("quiz_sessions")
                ? "DELETE FROM quiz_sessions WHERE id IN (SELECT session_id FROM quiz_session_questions WHERE question_id IN (" + doomed + "))"
                : "DELETE FROM " + table + " WHERE question_id IN (" + doomed + ")";
            Query query = entityManager.createNativeQuery(sql).setParameter("moduleId", moduleId);
            if (keepLiteral != null) query.setParameter("keepSortOrders", keepLiteral);
            query.executeUpdate();
        }
    }
    public void deleteForQuestion(UUID questionId) {
        entityManager.createNativeQuery("DELETE FROM quiz_sessions WHERE id IN (SELECT session_id FROM quiz_session_questions WHERE question_id=:id)").setParameter("id", questionId).executeUpdate();
        for (String table : new String[]{"interview_answers", "daily_challenge_assignments", "study_attempts", "srs_cards"})
            entityManager.createNativeQuery("DELETE FROM " + table + " WHERE question_id=:id").setParameter("id",questionId).executeUpdate();
    }

    /** Mảng PostgreSQL dạng literal `{1,2,3}`; phần tử là `int` nên chỉ cần lọc ký tự số và dấu `-`. */
    static String toPgIntArrayLiteral(Collection<Integer> values) {
        return values.stream()
                .map(String::valueOf)
                .filter(value -> value.matches("-?\\d+"))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
