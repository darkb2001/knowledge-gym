# REST API Design — Knowledge Gym

> Base URL: `/api/v1` — đây là **servlet context-path** (`server.servlet.context-path`), áp dụng cho
> **mọi** endpoint bên dưới (controller mapping không có prefix `api/v1`).
> Auth: JWT Bearer (access) + refresh httpOnly cookie  
> Total: ~75 endpoints (một số là aspirational — xem ghi chú từng nhóm)  
> **ADR-002:** single-tenant — **không** có field/param `tenant`. Enum strategy/status = **UPPERCASE**.  
> Schema: `07-erd.md` + DDL `07-erd-ddl.sql`. Bookmark = `notes.note_type = BOOKMARK`.
>
> **Trạng thái:** m4–m10 đã triển khai phần MVP tương ứng. Blog Collector/public blog (m9) và AI Writer queue, schedule, revisions/review (m10) hiện có; Hermes connector, social posting, notifications, PDF export và WebSocket vẫn deferred.

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

POST   /auth/change-password    Đổi mật khẩu khi ĐÃ đăng nhập (cần mật khẩu hiện tại)
       Headers: Authorization: Bearer ***    Cookie: refreshToken (phiên đang gọi — được GIỮ lại)
       Body:    { currentPassword, newPassword, confirmPassword }
       Returns: 200 { message }  — thu hồi refresh family của MỌI phiên khác, phiên hiện tại vẫn dùng được
       Lỗi:     400 sai mật khẩu hiện tại, mật khẩu mới trùng mật khẩu cũ, 2 lần nhập lệch nhau,
                     hoặc mật khẩu mới ngoài 8..72 ký tự
                409 tài khoản chưa có mật khẩu (đăng nhập Google) → dùng /auth/set-password

POST   /auth/password-code/request   Gửi mã 6 số tới email của chính phiên đang đăng nhập
       Headers: Authorization: Bearer ***   (không nhận email trong body — lấy từ JWT)
       Returns: 202 { message }   — dùng cho luồng set-password của tài khoản OAuth

POST   /auth/set-password       Đặt mật khẩu LẦN ĐẦU cho tài khoản đăng nhập Google
       Headers: Authorization: Bearer ***
       Body:    { code, newPassword, confirmPassword }
       Returns: 200 { message }  — vẫn đăng nhập Google được, đồng thời bật đăng nhập email + mật khẩu
       Lỗi:     400 mã sai / hết hạn / quá 5 lần thử, 409 tài khoản đã có mật khẩu (→ /auth/change-password)
```

## User Endpoints

```
GET    /users/me                Get current user profile
       Returns: { id, email, displayName, avatarUrl, role, authProvider, hasPassword, xp, stats }
       hasPassword=false → tài khoản chỉ đăng nhập Google (password_hash NULL): FE hiện luồng ĐẶT mật khẩu

PATCH  /users/me                Update profile
       Body:    { displayName?, avatarUrl? }

GET    /users/me/progress       Get user progress across all modules       [m7]
       Returns: List<{ moduleId, masteryPct, totalAttempts, streak }>

GET    /users/me/stats          Get user stats                              [m7]
       Returns: { xp, currentStreak, longestStreak: null, level: null, badges: [] }
       level / badges / longestStreak = [m12] — trả null/[] thay vì giá trị bịa

GET    /users/me/bookmarks      Get user's bookmark notes                    [m8]
       Impl:    notes WHERE user_id=me AND note_type='BOOKMARK'
       Returns: List<Note>

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
GET    /search                  Global search (questions + caller's notes + published blogs) [m9, ES m11]
       Query:   ?q=
       Impl:    Elasticsearch index `knowledge-gym-search` (m11), fed from PostgreSQL via the
                `search_outbox` triggers; falls back to the PostgreSQL tsvector query when the
                index is unavailable or `app.search.elasticsearch.enabled=false`
       Returns: JSON array of { type, id, title, excerpt }   (bare array, not an envelope)

       Ghi chú: hành vi trả về vẫn là mảng phẳng `[{type,id,title,excerpt}]` như m9 — bật ES chỉ
       đổi backend xếp hạng, KHÔNG đổi shape. Index và truy vấn dùng CHUNG `SearchText`
       (bỏ dấu + stopword + n-gram + synonym Việt–Anh); truy vấn không dấu vẫn khớp nội dung có dấu.
       ES chỉ dùng để xếp hạng/scale, không đảm nhiệm ngôn ngữ học.
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
                      startedAt, finishedAt, questionIds },
            questions: [{ questionId, title }] }

POST /mock-interview/{id}/answer → 200  [KHÔNG còn tồn tại trong code — câu trả lời chỉ được ghi
Body: { questionId, userAnswer }        khi POST /mock-interview/{id}/submit; xem ghi chú dưới]
Response: { sessionId, questionId, userAnswer, answerHtml, attemptedAt }

POST /mock-interview/{id}/finish → 200, session với status=FINISHED
POST /mock-interview/{id}/cancel → 200, session với status=CANCELLED (bỏ phiên; idempotent —
     huỷ lại trả nguyên phiên đã đóng chứ không 409; phiên user khác → 404, ẩn danh → 401)
GET /mock-interview/{id} → 200 resume phiên ở MỌI status (reload trang):
     { session: {...}, totalQuestions, answeredCount,
       questions: [{ questionId, title, answered }] }  (đúng thứ tự đã giao;
     ẩn danh → 401, phiên user khác → 404)
GET /mock-interview/history?page=1&size=20 → { items, page, size, totalElements, totalPages }
```

TEXT-only; AUDIO → 400. userAnswer phải có nội dung, tối đa 20.000 ký tự.
Câu hỏi chọn qua topic → modules → questions; số thực tế có thể thấp hơn questionCount.
Tập câu được giao persist thành placeholder `interview_answers` (`user_answer IS NULL`):
không thêm bảng membership thứ hai. Answer chỉ chấp nhận câu được giao và upsert theo
UK `(session_id, question_id)`, đúng 1 row/câu; cột DB là `user_answer`.
**Không chấm điểm:** nộp câu trả lời trả về ngay đáp án mẫu của câu hỏi (`answerHtml`, HTML đã
sanitize — cùng nguồn với question detail) để người học tự đối chiếu. Không có `keywordScore`,
`overallScore`, `feedback`; bảng `interview_answers` lưu `answer_html` thay cho
`keyword_score`/`feedback`/`sample_answer` (V035).
Row lock serialize answer/finish; phiên đã FINISHED → 409, phiên user khác → 404.
`cancel` là terminal thứ hai (V037 mở rộng CHECK thêm `CANCELLED`); phiên đã đóng thì cancel no-op.
`GET /{id}` trả `answered`/`answeredCount` theo `interview_answers` đã persist — luồng hiện tại chỉ
ghi answer lúc `/submit`, nên phiên đang ACTIVE thường `answeredCount = 0`.
`questionIds` trong session giữ **đúng thứ tự đã giao** (`interview_answers.display_order`, V017).
Response đi qua DTO (`rest/interview/dto`) nên không lộ trường nội bộ của domain.
Xoá câu dọn answer nhưng giữ interview session; FK topic giữ nguyên.
FE `/mock-interview` có text editor, hiện đáp án tham khảo sau khi lưu, sửa answer, finish và lịch sử.

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
GET    /notes                   Get user's notes [m8]
       Query:   none

POST   /notes                   Create private note (owner inferred from JWT) [m8]
       Body:    { questionId?, moduleId?, noteType, content, tags }
       noteType: "QUICK" | "STUDY" | "HIGHLIGHT" | "BOOKMARK"

GET    /notes/{id}              Get owned note [m8]

PUT    /notes/{id}              Update owned note [m8]

DELETE /notes/{id}              Delete owned note [m8]

POST   /notes/{id}/convert      Convert question-linked note to SRS card [m8]
       Body:    none; note must reference a question, existing card is returned idempotently

GET    /notes/public            Deferred (notes are private in m8)

POST   /notes/{id}/share        Deferred

GET    /notes/search            Deferred; use global /search
       Query:   ?q=

GET    /notes/export?format=md  Export as Markdown [m8]; PDF deferred
       Query:   ?format=md (PDF deferred)
```

## Blog Endpoints

```
GET    /blog/posts              List published posts (envelope phân trang) [m9]
       Query:   ?tag=&page=&size=   page 0-based (default 0); size default 10, cap 50
       Returns: { items: [ { id, title, slug, excerpt, publishedAt, viewCount, likeCount, tags } ],
                  total, page, size, hasMore }

GET    /blog/posts/{slug}       Get published post [m9]

POST   /blog/posts/{slug}/like Ensure liked (idempotent via blog_post_likes UK) [m9]
       Returns: 200 { liked: true }

DELETE /blog/posts/{slug}/like Ensure unliked (idempotent) [m9]
       Returns: 200 { liked: false }

POST   /blog/posts/{slug}/view Track authenticated view (idempotent UK post+user) [m9]

GET    /blog/posts/{slug}/comments Get comments [m9]

POST   /blog/posts/{slug}/comments Post sanitized comment [m9]
       Body:    { content, parentId? } (authenticated)
       Comment (list + create) thêm authorDisplayName/authorAvatarUrl (join users, không N+1);
       giữ nguyên các field cũ.

GET    /blog/feed.rss           RSS feed [m9]

POST   /admin/blog/collect      Run due collectors (ADMIN) [m9]
POST   /admin/blog/posts        Create sanitized manual draft (ADMIN) [m9]
POST   /admin/blog/posts/{id}/publish Publish draft; writes outbox event (ADMIN) [m9]

GET    /admin/blog/writer/settings Read schedule/publish policy and next run (ADMIN) [m10]
PUT    /admin/blog/writer/settings Update enabled/timezone/daily limit/policy/threshold (ADMIN) [m10]
POST   /admin/blog/writer/runs Enqueue a generation (ADMIN; requestId idempotency key) [m10]
GET    /admin/blog/writer/runs/{id} Read queue status [m10]
GET    /admin/blog/writer/stats Read daily/monthly usage and review/failure counts [m10]
GET    /admin/blog/writer/review List REVIEW drafts [m10]
GET    /admin/blog/writer/posts/{id} Read a private draft preview (ADMIN) [m10]
GET    /admin/blog/writer/posts/{id}/revisions Read immutable revision history [m10]
POST   /admin/blog/writer/posts/{id}/revise Ask AI to revise a draft (ADMIN) [m10]
PUT    /admin/blog/writer/posts/{id} Save a manual edit as a new revision (ADMIN) [m10]
POST   /admin/blog/writer/posts/{id}/revisions/{version}/restore Restore content as a new revision (ADMIN) [m10]
POST   /admin/blog/writer/posts/{id}/publish Explicitly approve and publish (ADMIN) [m10]
POST   /admin/blog/writer/posts/{id}/reject Archive a review draft (ADMIN) [m10]
```

## Agent Endpoints (Admin)

```
Legacy `/agent/*` routes are design-only. Use `/admin/blog/collect` for M9 collection and `/admin/blog/writer/*` for M10 writing and review. The OpenAI key is configured on the backend and never sent to these API requests.
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

> **m7 ship đúng 5 endpoint dưới đây.** `/dashboard/overview` (bản cũ trong doc này) **không** ship ở
> m7: `weaknessAreas` thuộc m12 (m12 plan đã nhận "weakness radar"), `nextReviewCount` đã có sẵn ở
> `QueryDueUseCase` (m5) nên không cần gộp lại. `level`/`badges`/`longestStreak` chưa có cột ở DB ⇒
> trả `null`/`[]`, **không** bịa giá trị — hiện thực thật ở m12 (`user_stats` + `user_badges`).

```
GET    /dashboard/radar          Knowledge radar (mastery per module)
       Source: user_progress (GROUP BY module_id) — KHÔNG đọc MVIEW user_topic_mastery (chưa refresh)
       Returns: { modules: [{ moduleId, name, masteryPct }] }
                weaknessRank chưa ship ở m7 — tên field cũ để lại đây gây hiểu sai, đã bỏ khỏi response
                (weakness per-topic = m12). Đừng trả một số không có định nghĩa nào trong docs.

GET    /dashboard/heatmap        Activity heatmap
       Query:   ?days=90 (default 90, cap 365)
       Returns: [ { date, count } ] — đủ `days` phần tử, gồm ngày count = 0
       Bucket:  (attempted_at AT TIME ZONE :zone)::date, zone = app.progress.timezone

GET    /dashboard/leaderboard    Top 100 theo XP
       Source: Redis sorted set lb:global (cache-aside, TTL 1h) → miss ⇒ rebuild từ PG
       Rebuild: ZADD vào lb:global:tmp:{uuid} (unique mỗi lần) → EXPIRE 60 → RENAME (atomic)
       Filter: chỉ user có xp > 0; member = "%010d:%s" (xp, userId) để tie-break tất định
       Returns: [ { rank, userId, displayName, xp } ] — tie-break tất định khi XP bằng nhau

GET    /users/me/progress       Get user progress across all modules
       Returns: List<{ moduleId, masteryPct, totalAttempts, streak }>
       streak tính on-read từ study_attempts (user_progress.streak_days KHÔNG dùng ở m7)

GET    /users/me/stats          Get user stats
       Returns: { xp, currentStreak, longestStreak: null, level: null, badges: [] }
       xp = users.xp (ghi cùng tx với attempt); recompute từ study_attempts
            (DISTINCT ON question_id, chỉ tính lần trả lời ĐẦU TIÊN của mỗi câu) khi cần đối chiếu
       currentStreak tính on-read từ study_attempts (bucket theo app.progress.timezone)
       longestStreak / level / badges = [m12]
```

### Dashboard endpoints chưa ship (ghi rõ phase)

```
GET    /dashboard/overview       [m12] todayProgress + recentActivity + weaknessAreas
GET    /users/me/bookmarks       [m8] notes WHERE note_type='BOOKMARK'
WS     /ws/progress              [de-scope → REST polling]
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
