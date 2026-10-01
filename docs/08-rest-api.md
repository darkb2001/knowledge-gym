# REST API Design — Knowledge Gym

> Base URL: `/api/v1` — đây là **servlet context-path** (`server.servlet.context-path`), áp dụng cho
> **mọi** endpoint bên dưới (controller mapping không có prefix `api/v1`).
> Auth: JWT Bearer (access) + refresh httpOnly cookie  
> Total: ~75 endpoints (một số là aspirational — xem ghi chú từng nhóm)  
> **ADR-002:** single-tenant — **không** có field/param `tenant`. Enum strategy/status = **UPPERCASE**.  
> Schema: `07-erd.md` + DDL `07-erd-ddl.sql`. Bookmark = `notes.note_type = BOOKMARK`.
>
> **Trạng thái:** m4 (m4a backend + m4b frontend) đã ship. m4a: content parser + REST + cache + Swagger + RBAC.
> m4b: Next.js 14 frontend (auth + question browser). **m5 đã ship:** SRS + SM-2 + FlashcardDeck
> (`/srs/enroll` dual-mode, `/srs/due`, `/srs/review/{cardId}` + FE flip card). **m6 đã triển khai:** Quiz + Mock Interview TEXT (backend + frontend).
> Code Challenge / Notes / Blog / Agent / Dashboard / Notification / Export / WebSocket là **phase sau** — giữ ở đây làm thiết kế, **chưa** implement.

---

## Auth Endpoints (Module 06 + 15)

```
POST   /auth/register           Register new user
       Body:    { email, password, displayName }
       Returns: 201 { accessToken, expiresIn, user }
                + Set-Cookie: refreshToken (httpOnly; Secure; SameSite=Strict)

POST   /auth/login              Login with email + password
       Body:    { email, password }
       Returns: 200 { accessToken, expiresIn, user }
                + Set-Cookie: refreshToken (httpOnly; Secure; SameSite=Strict)
       Tokens:  access = JWT 15m (Bearer); refresh = opaque UUID 7d (cookie only)

POST   /auth/refresh            Rotate refresh → new access (+ new refresh cookie)
       Cookie:  refreshToken
       Returns: 200 { accessToken, expiresIn }
                + Set-Cookie: refreshToken (rotated)

POST   /auth/logout             Logout (invalidate refresh token family)
       Headers: Authorization: Bearer {accessToken}
       Returns: 204

GET    /oauth2/authorization/google       OAuth2 Google login (redirect sang Google)  [note: context-path `/api/v1` already applied]
       → redirect: /oauth2/callback/google (Spring Security handler)
       → chỉ nhận account có email_verified=true
       → same token pair as login (access JWT + refresh cookie)

POST   /auth/forgot-password    Request password reset — 6-digit code (Gmail dev / Mailu prod)
       Body:    { email }
       Returns: 200 { message }  (luôn 200 — tránh email enumeration)
       Gửi email chứa code 6 chữ số, TTL 10 phút, max 5 lần thử sai
       (không phải session token — OTP riêng, Redis pwd_reset:{email} + PG password_reset_codes)

POST   /auth/reset-password     Xác nhận mã + đặt mật khẩu mới
       Body:    { email, code, newPassword }
       Returns: 200 { message }  — revoke toàn bộ refresh token family
```

## User Endpoints

```
GET    /users/me                Get current user profile
       Returns: { id, email, displayName, avatarUrl, role, authProvider, xp, stats }

PATCH  /users/me                Update profile
       Body:    { displayName?, avatarUrl? }

GET    /users/me/progress       Get user progress across all modules
       Returns: List<{ moduleId, masteryPct, totalAttempts, streak }>

GET    /users/me/stats          Get user stats (XP, badges, streak)
       Returns: { xp, level, badges[], currentStreak, longestStreak }

GET    /users/me/bookmarks      Get bookmarked questions
       Impl:    notes WHERE user_id=me AND note_type='BOOKMARK'
       Returns: List<QuestionDTO>

DELETE /users/me                Delete account (soft delete)
```

## Topics & Modules Endpoints

> **Shipped ở m4a** — `GET /topics`, `GET /topics/{slug}`, `GET /modules`, `GET /modules/{id}`.
> `GET /modules` trả **List** (không phân trang); `GET /modules/{id}/mindmap` là phase sau.

```
GET    /topics                  List all topics (m4a)
       Returns: List<TopicDTO> { id, slug, name, description, displayOrder, moduleCount }
       Cache:   @Cacheable("topics", key="'all'")

GET    /topics/{slug}           Get topic detail (m4a)
       Returns: TopicDTO  — 404 (RFC 7807) nếu slug không tồn tại
       (không trả kèm modules[] — dùng GET /modules?topicId=)

GET    /modules                 List modules (m4a)
       Query:   ?topicId=
       Returns: List<ModuleDTO> { id, slug, name, description, displayOrder, topicId, topicSlug, questionCount }

GET    /modules/{id}            Get module detail (m4a)
       Returns: ModuleDTO  — 404 nếu id không tồn tại

GET    /modules/{id}/mindmap    Get mindmap structure                 [phase sau — chưa ship]
       Returns: { nodes[], edges[] }
```

## Questions Endpoints

> **Shipped ở m4a:** `GET /questions` (list/filter/search) và `GET /questions/{id}` (detail).
> Admin create/update/delete nằm ở nhóm **Admin Content** (`/admin/content/questions...`), không phải `/questions`.
> Bookmark + random question là phase sau.

```
GET    /questions               Search & filter questions (m4a)
       Query:   ?moduleId=          UUID, lọc theo module
                &tag=               tag slug (khớp ANY(tags))
                &difficulty=        JUNIOR|MID|SENIOR (không phân biệt hoa/thường; giá trị lạ → 400)
                &q=                 full-text search (PostgreSQL tsvector, config 'simple')
                &page=              1-based (mặc định 1)
                &size=              mặc định 20, tối đa 100 (vượt → clamp về 100)
       Returns: { items[], page, size, totalElements, totalPages }
       items:   QuestionSummaryDTO { id, moduleId, moduleSlug, title, difficulty, tags, sortOrder }
                — KHÔNG có answerHtml (payload nhẹ cho list)
       Sort:    có `q` → ts_rank DESC; không `q` → module display_order, question sort_order
       Note:    page là 1-based (khác Spring Data 0-based)

GET    /questions/{id}          Get single question with full answer (m4a)
       Returns: QuestionDetailDTO { id, moduleId, moduleSlug, title, answerHtml, difficulty,
                                    tags, sortOrder, options[] }
                options: QuestionOptionDTO { id, content, displayOrder } — KHÔNG có isCorrect
       Cache:   @Cacheable("questions", key="#id")  — 404 nếu id không tồn tại
       Note:    options rỗng ở m4a (quiz sinh ở m6); để FE không phải đổi shape sau

GET    /questions/random        Get random question                    [phase sau — chưa ship]

POST   /questions/{id}/bookmark Toggle bookmark                        [phase sau — chưa ship]
```

## Global Search

```
GET    /search                  Global search (questions + notes + blog)   [phase sau — chưa ship]
       Query:   ?q=&types=questions,notes,blog&page=&size=
       Impl:    Elasticsearch primary; fallback PG tsvector (de-scope ladder #7)
       Returns: { hits: [{ type, id, title, snippet, score }] }

       Ghi chú: m4a mới chỉ có full-text search **trong** `GET /questions?q=` (PostgreSQL tsvector
       trên bảng `questions`). Endpoint `/search` gộp nhiều nguồn chưa implement.
```

## SRS / Flashcard Endpoints

**Đã ship ở m5.** `userId` luôn lấy từ JWT principal — không có field `userId` trong body/query.

```
POST   /srs/enroll              Enroll vào SRS deck (dual-mode, idempotent)
       Body:    { moduleId }                       → Mode A: cả module, auto deck theo module
                { questionIds[], deckId? }         → Mode B: đúng list câu (custom deck)
       Gửi cả hai hoặc không gửi gì → 400
       Returns: 201 { enrolled: int, cardIds[], deckId }
       Re-enroll không tạo card trùng (UK user_id+question_id) → enrolled: 0

GET    /srs/due                 Thẻ đến hạn, sắp theo nextReview
       Query:   ?moduleId=&limit=
       Returns: List<SRSCardDTO>
       { cardId, questionId, moduleId, moduleSlug, title, answerHtml,
         difficulty, repetitions, easeFactor, intervalDays, nextReview }

POST   /srs/review/{cardId}     Ghi kết quả 1 lần ôn (SM-2 + study_attempts cùng tx)
       Body:    { quality: 0-3, timeMs? }     quality ngoài 0-3 → 400
       Returns: { cardId, quality, intervalDays, easeFactor, repetitions,
                  nextReview, correct }

GET    /srs/stats               Get SRS statistics
       Returns: { dueToday, learned, mature, young }
       (⚠️ chưa implement — m7 Dashboard)

POST   /srs/reset               Reset all SRS cards
       (⚠️ chưa implement)
```

Bảng mapping `quality` (canonical — xem `Sm2Scheduler`):

| quality | Nhãn FE | interval | ease | repetitions |
|---|---|---|---|---|
| 0 | Again | 1 ngày | −0.2 | 0 (reset) |
| 1 | Hard | ceil(prev × 1.2) | −0.14 | +1 |
| 2 | Good | ceil(prev × ease) | giữ nguyên | +1 |
| 3 | Easy | ceil(prev × ease × 1.3) | +0.1 | +1 |

(`ceil` làm tròn lên; ease floor 1.3; first review: Again/Hard/Good → 1 ngày, Easy → 4 ngày.)

`timeMs` optional — FE m5 luôn gửi (ms từ lúc thẻ được hiển thị, **không** phải từ lúc lật). BE lưu vào `study_attempts.time_ms`;
không gửi thì lưu `NULL` (analytics phân biệt "không đo" với "0 ms"), **không** default số giả.

Mỗi lần review ghi thêm 1 row `study_attempts`: `source = 'FLASHCARD'`,
`is_correct = (quality >= 2)`, `answer = NULL`.

## Quiz Endpoints (m6a + m6b)

```text
POST /quiz/generate → 201
Body: { moduleId, count (1..50), strategy, difficulty? }
strategy: RANDOM | WEAKNESS | INTERVIEW | SPACED
Response: { id, strategy, score, total, startedAt, finishedAt,
            questions: [{ questionId, title, options: [{ id, content }] }], timeLimit }

POST /quiz/{id}/submit → 200
Body: { answers: [{ questionId, selectedOptionId?, timeMs? }] }
Response: { sessionId, score, correctCount, total,
            breakdown: [{ questionId, correct, selectedOptionId, correctOptionId }] }

GET /quiz/{id} → cùng shape với generate, chỉ chủ sở hữu
GET /quiz/history?page=1&size=20 → { items, page, size, totalElements, totalPages }
items: [{ id, strategy, score, total, startedAt, finishedAt }]
POST /admin/content/questions/generate-options → { questions, eligible, options }
```

- Options được sinh tự động khi import **và khi admin tạo/sửa câu hỏi**; endpoint backfill chỉ
  ADMIN, evict cache questions. Chạy lại với cùng nội dung giữ nguyên option IDs. Options public
  không chứa cờ đúng.
- `count` là trần; session có thể ít câu hơn nếu pool chưa đủ MCQ. Pool rỗng → 409.
  WEAKNESS ưu tiên câu từng sai, INTERVIEW ưu tiên MID/SENIOR; SPACED **chỉ** lấy thẻ đến hạn.
- `timeLimit = số câu thực tế × 60` giây, **FE-only**. Không có cột time limit hoặc server deadline;
  timer browser tự nộp khi hết giờ, không phải cơ chế chống gian lận.
- `score = round(correctCount / total × 100)`. Bỏ trống tính sai; có thể gửi `answers: []`.
  Mỗi câu của session vẫn có 1 `quiz_answers` và 1 `study_attempts(PRACTICE)` với score 0/100.
- Chặn null element, trùng questionId, câu ngoài phiên và option thuộc câu khác → 400.
  Mọi thao tác get/submit scope user lấy từ JWT; phiên người khác → 404.
- Submit chốt phiên cùng transaction với PRACTICE. Lần hai → 409; row lock + DB UK bảo vệ khi
  Redis chết. Redis chỉ debounce fail-open, xem `12-redis-strategy.md` §9.
- Xoá câu hỏi qua admin/re-import xoá toàn bộ quiz session chứa câu đó, tránh score/total lệch.
- Phân trang 1-based, size 1..100. FE route `/quiz/[moduleId]` có timer, progress, breakdown,
  lịch sử và error/empty state; entry tại question browser khi chọn module.

## Mock Interview Endpoints (m6c)

```text
POST /mock-interview/start → 201
Body: { topicId, questionCount (1..20), mode: "TEXT" }
Response: { session: { id, userId, topicId, questionCount, mode, status,
                      overallScore, startedAt, finishedAt, questionIds },
            questions: [{ questionId, title }] }

POST /mock-interview/{id}/answer → 200
Body: { questionId, userAnswer }
Response: { sessionId, questionId, userAnswer, keywordScore, feedback, sampleAnswer, attemptedAt }

POST /mock-interview/{id}/finish → 200, session với status=FINISHED
GET /mock-interview/history?page=1&size=20 → { items, page, size, totalElements, totalPages }
```

TEXT-only; AUDIO → 400. userAnswer phải có nội dung, tối đa 20.000 ký tự.
Câu hỏi chọn qua topic → modules → questions; số thực tế có thể thấp hơn questionCount.
Tập câu được giao persist thành placeholder `interview_answers` có `keyword_score=NULL`:
không thêm bảng membership thứ hai. Answer chỉ chấp nhận câu được giao và upsert theo
UK `(session_id, question_id)`, đúng 1 row/câu; cột DB là `user_answer`.
Keyword grader thuần Java so khớp từ nguyên vẹn, chuẩn hoá hoa/thường và dấu tiếng Việt.
Finish lấy trung bình **chỉ answer đã nộp** (`keyword_score IS NOT NULL`), không answer → 0.
Row lock serialize answer/finish; phiên đã FINISHED → 409, phiên user khác → 404.
`questionIds` trong session giữ **đúng thứ tự đã giao** (`interview_answers.display_order`, V017).
Response đi qua DTO (`rest/interview/dto`) nên không lộ trường nội bộ của domain.
Xoá câu dọn answer nhưng giữ interview session (và điểm đã chốt); FK topic giữ nguyên.
FE `/mock-interview` có text editor, chấm từ khóa, sửa answer, sample answer, finish và lịch sử.

## Code Challenge Endpoints

```
GET    /challenges              List challenges
       Query:   ?difficulty=&tag=

GET    /challenges/{id}         Get challenge detail
       Returns: { title, prompt, starterCode, testCases[], hints[] }

POST   /challenges/{id}/submit  Submit code
       Body:    { code: String }
       Returns: { passed: int, total: int, testResults[], runtimeMs }

POST   /challenges/{id}/hint    Get next hint
```

## Notes Endpoints

```
GET    /notes                   Get user's notes
       Query:   ?moduleId=&type=&tag=&page=&size=

POST   /notes                   Create note
       Body:    { questionId?, moduleId, type, content, tags, isPublic }
       type:    "QUICK" | "STUDY" | "HIGHLIGHT" | "BOOKMARK"

GET    /notes/{id}              Get single note

PATCH  /notes/{id}              Update note

DELETE /notes/{id}              Delete note

POST   /notes/{id}/convert      Convert note to flashcard
       Body:    { front, back }

GET    /notes/public            Get public notes

POST   /notes/{id}/share        Share note

GET    /notes/search            Full-text search (notes only)
       Query:   ?q=

GET    /notes/export            Export as Markdown/PDF
       Query:   ?format=md|pdf
```

## Blog Endpoints

```
GET    /blog/posts              List published posts
       Query:   ?topic=&page=&size=&sort=

GET    /blog/posts/{slug}       Get single post

POST   /blog/posts/{id}/like    Toggle like (idempotent via blog_post_likes UK)
       Returns: 200 { liked: boolean, likeCount }

DELETE /blog/posts/{id}/like    Unlike (same as toggle when liked=true)
       Returns: 200 { liked: false, likeCount }

POST   /blog/posts/{id}/view    Track view (idempotent UK post+user)

GET    /blog/posts/{id}/comments   Get comments

POST   /blog/posts/{id}/comments   Post comment
       Body:    { content, parentId? }

GET    /blog/feed.rss           RSS feed
```

## Agent Endpoints (Admin)

```
POST   /agent/collector/run     Trigger data collection
       Returns: 202 { jobId }

GET    /agent/collector/runs    Run history

GET    /agent/collector/items   List collected items
       Query:   ?sourceId=&category=&used=

POST   /agent/writer/run         Trigger blog generation
       Body:    { topicId, strategy }

GET    /agent/writer/queue       Generation queue

POST   /agent/writer/{postId}/approve    Approve (publish)

POST   /agent/writer/{postId}/reject     Reject (with reason)

GET    /agent/runs               List all agent runs
```

## Admin Endpoints

> **Shipped ở m4a:** nhóm **Admin Content** (dưới). Các endpoint admin khác (users, stats, audit-logs)
> là phase sau.
>
> Toàn bộ `AdminContentController` có `@PreAuthorize("hasRole('ADMIN')")` → user thường nhận **403**
> (Problem Details, không phải HTML). Mọi mutation đồng bộ **evict cả 3 cache** `questions`/`topics`/`modules`
> (`topics`/`modules` chứa `moduleCount`/`questionCount` phái sinh nên 1 thay đổi câu hỏi làm số đếm sai ngay).

```
POST   /admin/content/parse     Parse docs/ folder → import vào DB (m4a)
       Returns: 202 { jobId, status }   status = "RUNNING"
       Chạy BẮT BUỘC async (không giữ kết nối) — import 15 file HTML + upsert có thể vượt
       timeout của reverse proxy. Job chạy nền trong **1 transaction** (rollback nếu fail),
       evict 3 cache trong finally. Idempotent: natural key (topic/module = slug,
       question = (module_id, sort_order)).

GET    /admin/content/jobs/{jobId}   Trạng thái job import (ADMIN) (m4a)
       Returns: 200 { jobId, status, startedAt, finishedAt?, result?, errorMessage? }
                status ∈ RUNNING | SUCCEEDED | FAILED
                result: { topics, modules, questions, deleted, orphanModuleSlugs[] }
       Errors:  404 nếu jobId không còn trong registry in-memory (max 20 job / mất khi restart)

POST   /admin/content/questions    Create question (ADMIN) (m4a)
       Body:    { moduleId, title, answerHtml, difficulty?, tags?, sortOrder? }
       Returns: 201 AdminQuestionDTO { id, moduleId, title, answerHtml, difficulty, tags,
                                       searchKeywords, sortOrder, createdAt, updatedAt, options[] }
                options: AdminQuestionOptionDTO { id, content, isCorrect, displayOrder }
       Errors:  409 nếu `sortOrder` gửi lên đã bị chiếm trong module (pre-check + unique index
                `uk_questions_module_sort` là backstop dưới race); 404 nếu moduleId không tồn tại
       Note:    sortOrder null → tự lấy nextSortOrder. difficulty null → mặc định MID.
                answerHtml qua sanitizer (loại script/iframe/speak-notes; **giữ** flow-diagram).
                Option của câu này được sinh ngay sau khi ghi, nên `options` trong response là dữ
                liệu thật (≥2 option, đúng 1 `isCorrect`) chứ không phải mảng rỗng.
                Câu anh em trong module **không** bị sinh lại — tránh đổi option ID của quiz cũ.

PATCH  /admin/content/questions/{id}   Update question (ADMIN) (m4a)
       Body:    { title?, answerHtml?, difficulty?, tags? }  (field null = giữ nguyên)
       Returns: 200 AdminQuestionDTO
       Errors:  404 nếu id không tồn tại
       Note:    KHÔNG đổi được `sortOrder` (không có field này trong update request).
                Khi đổi title/answer/tags → tính lại `searchKeywords` (searchable_text).
                Option của câu này được sinh lại; nội dung option không đổi thì giữ nguyên ID. Nội
                dung đổi → option ID mới, `quiz_answers.selected_option_id` cũ thành NULL nhưng
                `answer_text` (snapshot lúc submit) vẫn giữ lựa chọn của user.

DELETE /admin/content/questions/{id}   Delete question (ADMIN) (m4a)
       Returns: 204  — 404 nếu id không tồn tại

GET    /admin/users             List users (paginated)                 [phase sau — chưa ship]
PATCH  /admin/users/{id}/role   Change user role                       [phase sau — chưa ship]
GET    /admin/stats             Platform-wide stats                    [phase sau — chưa ship]
GET    /admin/audit-logs        View audit logs                        [phase sau — chưa ship]
POST   /admin/content/import    Import from JSON/HTML                  [phase sau — chưa ship]
```

> **Ghi chú (m4a):** các endpoint admin content trước đây ghi nhầm ở `/questions`. Thực tế mount tại
> `/admin/content/...` (`@RequestMapping("/admin/content")`), không phải `/questions`.

## Dashboard / Analytics Endpoints

```
GET    /dashboard/overview       Dashboard data
       Returns: { todayProgress, streakCount, weekHeatmap[],
                   recentActivity[], weaknessAreas[], nextReviewCount }

GET    /dashboard/radar          Knowledge radar
       Returns: { modules: [{ name, masteryPct, weaknessRank }] }

GET    /dashboard/heatmap        Activity heatmap
       Query:   ?days=90
       Returns: List<{ date, count }>

GET    /dashboard/leaderboard    Top users
```

## Notification Endpoints

```
GET    /notifications            Get user notifications

PATCH  /notifications/{id}/read Mark as read

GET    /notifications/preferences  Get preferences

PATCH  /notifications/preferences  Update preferences
```

## Export Endpoints

```
GET    /export/notes.{format}    Export notes (md|pdf|json)

GET    /export/progress.{format} Export progress report

GET    /export/questions.{format} Export questions (md|pdf)
```

## WebSocket Endpoints (optional — de-scope → REST polling)

```
WS     /ws/progress              Live progress updates
       Topic: /user/{userId}/progress

WS     /ws/notifications         Real-time notifications

WS     /ws/agent-status          Agent status updates (admin)
```
