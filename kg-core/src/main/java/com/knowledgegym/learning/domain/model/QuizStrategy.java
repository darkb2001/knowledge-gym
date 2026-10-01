package com.knowledgegym.learning.domain.model;

/**
 * Chiến lược chọn câu cho 1 phiên quiz — phải khớp CHECK constraint `quiz_sessions.strategy` (V013).
 *
 * <p>Enum ở đây là **hợp đồng dữ liệu**: đổi tên hằng số mà quên migration là insert nổ CHECK ngay
 * ở request đầu tiên, nên hai chỗ này phải được đọc cùng nhau.
 */
public enum QuizStrategy {
    /** Trộn ngẫu nhiên trong module (có thể lọc theo difficulty). */
    RANDOM,
    /** Ưu tiên câu user từng trả lời sai (nguồn: `study_attempts.is_correct = FALSE`). */
    WEAKNESS,
    /** Ưu tiên câu độ khó MID/SENIOR, trộn độ khó như buổi phỏng vấn thật. */
    INTERVIEW,
    /** Câu đến hạn ôn theo SM-2 (`srs_cards.next_review <= today`). */
    SPACED
}
