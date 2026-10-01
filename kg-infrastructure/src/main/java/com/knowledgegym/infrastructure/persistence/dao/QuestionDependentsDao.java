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
 * <p><b>Bảo trì:</b> khi thêm bảng mới tham chiếu `questions(id)` mà không `ON DELETE CASCADE`
 * (V013 có `quiz_answers`, `interview_answers`, `daily_challenge_assignments` — hiện chưa phase
 * nào ghi row), phải bổ sung vào đây, nếu không re-import lại nổ FK. Không xoá sẵn các bảng đó
 * bây giờ vì "xoá lịch sử quiz/phỏng vấn" là quyết định nghiệp vụ thuộc phase sở hữu chúng.
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

        // study_attempts trước srs_cards: không bắt buộc về FK (cả hai trỏ tới questions, không trỏ
        // nhau), nhưng giữ thứ tự này để log/lock nhất quán giữa hai bảng.
        Query attempts = entityManager.createNativeQuery(
                "DELETE FROM study_attempts WHERE question_id IN (" + doomed + ")");
        Query cards = entityManager.createNativeQuery(
                "DELETE FROM srs_cards WHERE question_id IN (" + doomed + ")");
        for (Query query : new Query[]{attempts, cards}) {
            query.setParameter("moduleId", moduleId);
            if (keepLiteral != null) {
                query.setParameter("keepSortOrders", keepLiteral);
            }
        }
        attempts.executeUpdate();
        cards.executeUpdate();
    }

    /** Xoá thẻ SRS + attempt của một câu hỏi cụ thể (admin xoá thủ công). */
    public void deleteForQuestion(UUID questionId) {
        Query attempts = entityManager.createNativeQuery(
                "DELETE FROM study_attempts WHERE question_id = :questionId");
        Query cards = entityManager.createNativeQuery(
                "DELETE FROM srs_cards WHERE question_id = :questionId");
        attempts.setParameter("questionId", questionId);
        cards.setParameter("questionId", questionId);
        attempts.executeUpdate();
        cards.executeUpdate();
    }

    /** Mảng PostgreSQL dạng literal `{1,2,3}`; phần tử là `int` nên chỉ cần lọc ký tự số và dấu `-`. */
    static String toPgIntArrayLiteral(Collection<Integer> values) {
        return values.stream()
                .map(String::valueOf)
                .filter(value -> value.matches("-?\\d+"))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
