package com.knowledgegym.learning.domain.port;

import com.knowledgegym.learning.domain.model.StudyAttempt;

/**
 * Port ghi `study_attempts`. m5 chỉ dùng `save` (review flashcard); m6/m7 mở rộng thêm truy vấn
 * khi có quiz/dashboard — không thêm method "phòng trước".
 */
public interface StudyAttemptRepository {

    StudyAttempt save(StudyAttempt attempt);
}
