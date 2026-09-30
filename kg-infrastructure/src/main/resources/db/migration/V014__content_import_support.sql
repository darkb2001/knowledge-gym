-- V014 — Content import support (m4)
--
-- 1) topics.display_order — index.html chia topic theo `nav-group` có thứ tự;
--    V002 chỉ cho modules.display_order nên topics phải bổ sung để giữ đúng thứ tự.
-- 2) Natural key cho upsert idempotent: questions (module_id, sort_order).
--    sort_order = số thứ tự .qa-card trong file module → (module_id, sort_order) là duy nhất.
--    Không dùng title làm key vì title có thể trùng giữa các module.

ALTER TABLE topics ADD COLUMN display_order INT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_questions_module_sort ON questions (module_id, sort_order);
