package com.knowledgegym.learning.domain.port;

import com.knowledgegym.learning.domain.model.QuizAnswer;
import com.knowledgegym.learning.domain.model.QuizSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port cho `quiz_sessions` + `quiz_session_questions` + `quiz_answers`. Implementation ở
 * infrastructure.
 *
 * <p><b>Mọi truy vấn đều scope theo `userId`</b> (pattern m5): id session là UUID khó đoán, nhưng
 * không scope thì một user đọc/ghi được phiên của người khác chỉ bằng cách đổi id — IDOR. Sai thì
 * trả empty để use case dịch thành 404 (không 403: không xác nhận id có tồn tại hay không).
 */
public interface QuizSessionRepository {

    /** Insert phiên mới (kèm membership ở `quiz_session_questions`) hoặc update điểm/kết thúc. */
    QuizSession save(QuizSession session);

    Optional<QuizSession> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Như {@link #findByIdAndUserId} nhưng khoá row `SELECT ... FOR UPDATE`.
     *
     * <p>`quiz_sessions` không có cột `version` (V013), nên hai submit đồng thời cùng phiên đều đọc
     * `finished_at = NULL`, cùng chấm điểm và cùng ghi → lần ghi sau đè lần trước (lost update). UK ở
     * `quiz_answers` chặn được row trùng nhưng **không** chặn được việc chấm lại; khoá làm submit thứ
     * hai nối tiếp submit thứ nhất, thấy `finished_at != NULL` và trả 409.
     *
     * <p>Chỉ có hiệu lực khi gọi trong transaction (adapter dựa vào tx của use case).
     */
    Optional<QuizSession> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    /** Lịch sử của user, mới nhất trước. `page` 1-based. */
    List<QuizSession> findByUserId(UUID userId, int page, int size);

    long countByUserId(UUID userId);

    /**
     * Chèn answer theo lô, idempotent theo UK `(session_id, question_id)` (V016): row đã tồn tại bị
     * **bỏ qua**, không ghi đè.
     *
     * <p>Idempotent ở DB (không phải chỉ ở application) là yêu cầu của plan: Redis lock chỉ là
     * debounce và fail-open khi Redis chết, nên guard thật phải nằm ở ràng buộc dữ liệu.
     *
     * @param answers KHÔNG được chứa 2 phần tử cùng `questionId` (vi phạm làm câu lệnh `ON CONFLICT`
     *                của Postgres nổ "cannot affect row a second time"); use case đã dedupe trước khi
     *                gọi nên đây là ràng buộc nội bộ, không phải đường lỗi của client
     * @return số row thực sự được chèn
     */
    int insertAnswersIgnoringDuplicates(List<QuizAnswer> answers);
}
