-- V016 — Quiz/interview integrity (m6)
--
-- V013 tạo 4 bảng quiz/interview nhưng thiếu 3 thứ mà m6 (phase đầu tiên ghi row) cần:
--   1. `quiz_session_questions` — V013 không có chỗ lưu "session gồm những câu nào", nên
--      GET /quiz/{id} và breakdown trước submit không phục vụ được.
--   2. UK `(session_id, question_id)` — chống double-submit ở **DB**. Redis lock chỉ là debounce
--      (fail-open khi Redis chết), không phải hàng rào đúng.
--   3. Index cho 2 truy vấn history (`user_id, started_at DESC`) — V013 chỉ có PK.
--
-- ON DELETE CASCADE cho `question_id` (khác `NO ACTION` của V013): re-import xoá câu không được
-- làm nổ FK khi session cũ còn trỏ tới. `QuestionDependentsDao` vẫn xoá tường minh để log/lock
-- nhất quán, nhưng CASCADE là lưới an toàn cho đường xoá không đi qua dao.
--
-- `interview_sessions.topic_id` là NOT NULL REFERENCES topics(id) (V013): cố ý giữ nguyên hành vi
-- "xoá module/topic không cascade session phỏng vấn" — lịch sử phỏng vấn là bản ghi của user, không
-- phải dữ liệu nội dung. Test `deletingQuestionOfFinishedInterviewKeepsSession` cố định điều này.

CREATE TABLE quiz_session_questions (
    session_id    UUID NOT NULL REFERENCES quiz_sessions(id) ON DELETE CASCADE,
    question_id   UUID NOT NULL REFERENCES questions(id)     ON DELETE CASCADE,
    display_order INT  NOT NULL,
    PRIMARY KEY (session_id, question_id)
);

-- Tra ngược "câu này thuộc session nào" khi xoá câu hỏi.
CREATE INDEX idx_quiz_session_questions_qid ON quiz_session_questions (question_id);

ALTER TABLE quiz_answers
    ADD CONSTRAINT uk_quiz_answers_session_question UNIQUE (session_id, question_id);

-- Mock interview: 1 câu 1 row. Nộp lại cùng câu là upsert (ON CONFLICT DO UPDATE), không tạo row 2.
ALTER TABLE interview_answers
    ADD CONSTRAINT uk_interview_answers_session_question UNIQUE (session_id, question_id);

CREATE INDEX idx_quiz_sessions_user ON quiz_sessions (user_id, started_at DESC);
CREATE INDEX idx_quiz_answers_session ON quiz_answers (session_id);
CREATE INDEX idx_interview_sessions_user ON interview_sessions (user_id, started_at DESC);
CREATE INDEX idx_interview_sessions_status ON interview_sessions (user_id, status);
