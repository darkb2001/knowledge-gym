# M6 — Review và triển khai Quiz / Mock Interview

Ngày: 2026-10-01. Scope: m6a backend quiz, m6b frontend quiz, m6c mock interview TEXT.

## Findings đã sửa

| Mức | Finding ở phần M6 ban đầu | Hành vi sau sửa |
|---|---|---|
| High | Submit chấp nhận selectedOptionId thuộc câu khác | Validate option thuộc đúng question; 400 và không ghi row |
| High | Câu bỏ trống không có breakdown/attempt, PRACTICE.score NULL | Mỗi câu session có answer + attempt; bỏ trống = sai, score attempt 0/100 |
| High | Generate lưu membership trước khi loại câu đã biến mất | Chốt tập câu thực tế trước khi persist session |
| High | Chưa có adapter/wiring/API, FK cleanup vẫn chỉ SRS | Native SQL adapters cùng transaction, ownership, quiz/interview cleanup |
| Medium | SPACED lấp thêm câu chưa đến hạn | Chỉ chọn tập SRS due trong module/filter |
| Medium | Distractor trộn topic fallback trước khi đủ siblings | Ưu tiên module, deterministic và không trùng nội dung |
| Medium | Backfill thay IDs dù nội dung không đổi | Giữ IDs nếu content/correct/order không đổi |

## Triển khai

- V016: quiz membership, 2 UK session-question và index history; V017: `interview_answers.display_order`.
  17 migrations / 33 tables.
- Generate/submit/get/history quiz, 4 strategies và options import/backfill; public DTO không lộ cờ đúng.
- Submit: Redis debounce fail-open, session row lock, ON CONFLICT DO NOTHING; result + PRACTICE cùng tx.
- Mock interview TEXT: chọn qua topic/modules, persist assigned-question placeholders trong interview_answers,
  keyword_score NULL nghĩa chưa nộp; answer upsert, finish avg chỉ score đã nộp. AUDIO → 400.
- Quiz session chứa câu bị xoá được dọn toàn bộ; interview session được giữ, answer bị xoá.
- FE `/quiz/[moduleId]`: chọn strategy/difficulty/count, timer browser, progress, auto-submit,
  breakdown, lịch sử có phân trang và error/empty states.
- FE `/mock-interview`: chọn topic/count, editor, keyword feedback, sửa answer, sample, finish/history.
- Entry links ở question browser, đồng bộ docs API/ERD/Redis/teaching strategy.

## Validation

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew clean build --offline`:
  226 tests (129 core, 30 infrastructure, 67 presentation), gồm ArchUnit và Testcontainers.
- 9 integration tests M6: generate/submit/history, 33% với 1/3 câu đúng, bỏ trống,
  repeated submit 409, null/count/cross-option validation, IDOR, PRACTICE rollback,
  backfill IDs ổn định, mock upsert/average/finish, SPACED/WEAKNESS, cleanup FK.
- Test concurrent submit dừng Redis thật; 1 request thành công, 1 conflict, đúng 1 attempt/câu.
- Frontend: TypeScript, ESLint, 6 tests API client và production Next.js build.

## Giới hạn theo scope

Timer FE-only, không có server deadline. Distractor dùng heuristic từ nội dung cùng module/topic;
keyword grading không đánh giá ngữ nghĩa (AI grading m10). AUDIO và code challenge tiếp tục deferred.
Đã kiểm tra bằng build và API tests; chưa chạy browser E2E cho UI mới.
