-- Bỏ hệ thống chấm điểm mock interview (yêu cầu sản phẩm: submit → hiện đáp án mẫu, không cho điểm).
-- `interview_answers` giữ nguyên vai trò placeholder membership (`user_answer IS NULL` = chưa nộp);
-- `sample_answer` đổi tên thành `answer_html` vì giờ là đáp án mẫu trả cho client, không phải "đáp án để chấm".

ALTER TABLE interview_sessions DROP COLUMN IF EXISTS overall_score;
ALTER TABLE interview_answers DROP COLUMN IF EXISTS keyword_score;
ALTER TABLE interview_answers DROP COLUMN IF EXISTS feedback;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'interview_answers' AND column_name = 'sample_answer') THEN
        ALTER TABLE interview_answers RENAME COLUMN sample_answer TO answer_html;
    END IF;
END $$;
