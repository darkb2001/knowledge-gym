-- V037 — Mock interview: allow a terminal CANCELLED status (frontend "abandon session").
--
-- V013 tạo `interview_sessions.status` với CHECK chỉ cho ('ACTIVE','FINISHED'). Nút "huỷ phiên"
-- cần trạng thái kết thúc thứ hai để phân biệt user tự bỏ với user nộp bài. Constraint do V013
-- khai báo inline trên cột `status` nên Postgres đặt tên `interview_sessions_status_check`.
--
-- Prod Flyway không cho out-of-order nên version phải > 036 (top hiện tại) — dùng 037.

ALTER TABLE interview_sessions DROP CONSTRAINT IF EXISTS interview_sessions_status_check;

ALTER TABLE interview_sessions ADD CONSTRAINT interview_sessions_status_check
    CHECK (status IN ('ACTIVE', 'FINISHED', 'CANCELLED'));
