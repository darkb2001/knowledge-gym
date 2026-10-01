-- V017 — Thứ tự câu của phiên phỏng vấn (m6c)
--
-- V016 dùng `interview_answers` với vai trò kép: placeholder membership (`keyword_score` NULL) cho
-- tập câu đã giao, và row kết quả sau khi user trả lời. Bảng thiếu cột thứ tự, nên thứ tự câu chỉ
-- suy được từ `(attempted_at, id)` — mà mọi placeholder được insert trong **cùng một transaction**
-- nên `attempted_at` bằng nhau, còn `id` là UUID random. Hệ quả: `GET /mock-interview/{id}` (qua
-- history) trả câu lệch thứ tự so với lúc `start`.
--
-- `display_order` trở thành nguồn sự thật của thứ tự; `answer()` chỉ upsert nội dung chấm, không
-- đụng cột này.

ALTER TABLE interview_answers ADD COLUMN display_order INT;

-- Backfill theo thứ tự suy diễn cũ để cột NOT NULL hợp lệ với row đã tồn tại.
UPDATE interview_answers SET display_order = ranked.position
FROM (SELECT id, row_number() OVER (PARTITION BY session_id ORDER BY attempted_at, id) - 1 AS position
      FROM interview_answers) ranked
WHERE interview_answers.id = ranked.id;

ALTER TABLE interview_answers ALTER COLUMN display_order SET NOT NULL;
