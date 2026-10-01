# M7 — Progress + Dashboard

Ngày: 2026-10-01. Scope: progress/XP, streak/heatmap, Redis leaderboard cache, dashboard UI.

## Triển khai

- Mọi attempt từ flashcard và quiz đi qua `RecordAttemptUseCase` trong transaction hiện tại; attempt,
  mastery và XP rollback cùng nhau. XP chỉ cộng cho lần đầu mỗi câu, với advisory transaction lock
  theo cặp user/question và policy FLASHCARD 10, DAILY 15, PRACTICE 5 XP khi đúng, 1 khi sai.
- `user_progress` được gộp theo module và cập nhật bằng PostgreSQL `ON CONFLICT`; mastery tính từ
  tổng attempts/correct. Streak theo ngày hoạt động và timezone cấu hình được tính khi đọc.
- Stats đọc XP materialized từ `users.xp`; adapter cũng có truy vấn recompute từ attempts để đối chiếu.
- Thêm API radar, heatmap, leaderboard, user progress và user stats. Redis leaderboard rebuild từ
  `users.xp` qua key tạm duy nhất, TTL một giờ và marker 5 phút cho BXH rỗng; display name được batch
  load từ PostgreSQL.
- Dashboard `/dashboard` có radar SVG, lịch hoạt động 90 ngày, BXH, streak/XP, trạng thái loading,
  lỗi và dữ liệu rỗng; được bọc `RequireAuth` và có link trong header.
- V018 backfill XP/mastery từ attempts đã tồn tại trước M7; không thêm bảng/cột. Đồng bộ ERD, REST API,
  Redis strategy, cấu trúc dự án và phase plan.

## Validation

- Backend `clean build --offline` — BUILD SUCCESSFUL; unit/integration suite dùng Testcontainers PostgreSQL/Redis.
- Flyway Testcontainers xác nhận V018 backfill đúng XP theo attempt đầu tiên và mastery theo tổng luỹ kế.
- Frontend TypeScript, 6 tests API client, ESLint và production Next.js build — xanh.
- `git diff --check` — xanh. Browser visual E2E chưa chạy.

## Giới hạn

- Mock interview không ghi `study_attempts`, vì vậy không được tính trong dashboard M7.
- `longestStreak`, level, badges, topic leaderboard và interview attempts nằm ngoài scope M7.
