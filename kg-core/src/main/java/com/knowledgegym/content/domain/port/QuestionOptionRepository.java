package com.knowledgegym.content.domain.port;

import com.knowledgegym.content.domain.model.QuestionOption;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port cho `question_options` (V003).
 *
 * <p>m4 tạo bảng nhưng không ghi row nào; m6a là phase đầu tiên sinh option (MCQ cần option để
 * `quiz_answers.selected_option_id` có chỗ trỏ tới). Trước m6a mọi thao tác quiz MCQ là bất khả thi
 * về mặt dữ liệu, không phải chỉ thiếu endpoint.
 */
public interface QuestionOptionRepository {

    List<QuestionOption> findByQuestionId(UUID questionId);

    /**
     * Nhiều câu trong 1 query — sinh quiz cần option của cả session (tới 50 câu); gọi
     * {@link #findByQuestionId} trong vòng lặp là N+1.
     *
     * <p>Câu không có option **không** xuất hiện trong map (không map sang list rỗng) để caller
     * phân biệt được "chưa sinh" với "đã sinh nhưng rỗng".
     */
    Map<UUID, List<QuestionOption>> findByQuestionIds(Collection<UUID> questionIds);

    /**
     * Ghi đè option của câu — xoá hết option cũ rồi insert list mới trong **một** transaction.
     *
     * <p>Ghi đè (không phải append) vì option được sinh lại toàn bộ mỗi lần backfill; append sẽ
     * tích luỹ distractor cũ và câu có thể có 2 option đúng sau vài lần chạy.
     *
     * @return số option đã ghi
     */
    int replaceOptions(UUID questionId, List<QuestionOption> options);
    List<QuestionOption> saveManual(UUID questionId, List<QuestionOption> options);
    boolean isManual(UUID questionId);
}
