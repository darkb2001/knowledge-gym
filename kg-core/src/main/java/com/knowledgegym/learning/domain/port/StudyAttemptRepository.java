package com.knowledgegym.learning.domain.port;

import com.knowledgegym.learning.domain.model.StudyAttempt;

import java.util.List;
import java.util.UUID;

/**
 * Port ghi/đọc `study_attempts`. m5 dùng `save` (review flashcard); m6 thêm truy vấn cho chiến lược
 * quiz `WEAKNESS` — m7 dashboard sẽ thêm tiếp, không thêm method "phòng trước".
 */
public interface StudyAttemptRepository {

    StudyAttempt save(StudyAttempt attempt);

    /**
     * Câu mà user từng trả lời **sai**, xếp theo số lần sai giảm dần — nguồn dữ liệu cho chiến lược
     * `WEAKNESS` (P2 trong plan: `user_progress` còn rỗng tới m7 nên `study_attempts` là tín hiệu
     * duy nhất ở thời điểm m6).
     *
     * <p>Lưu ý quy ước m5: `is_correct = (quality >= 2)` nên **Hard tính là sai** — đúng ý niệm
     * "chưa nhớ chắc" mà `WEAKNESS` muốn nhắm tới.
     *
     * <p>Xếp hạng ở SQL (GROUP BY + ORDER BY) chứ không ở Java: bảng attempt của một user có thể
     * lớn dần theo thời gian, và chỉ top-N được dùng.
     */
    List<UUID> findWrongQuestionIdsByUser(UUID userId);
}
