# REST API Design — Knowledge Gym

> Base URL: `/api/v1` — đây là **servlet context-path** (`server.servlet.context-path`), áp dụng cho
> **mọi** endpoint bên dưới (controller mapping không có prefix `api/v1`).
> Auth: JWT Bearer (access) + refresh httpOnly cookie  
> Total: ~75 endpoints (một số là aspirational — xem ghi chú từng nhóm)  
> **ADR-002:** single-tenant — **không** có field/param `tenant`. Enum strategy/status = **UPPERCASE**.  
> Schema: `07-erd.md` + DDL `07-erd-ddl.sql`. Bookmark = `notes.note_type = BOOKMARK`.
>
> **Trạng thái:** m4 (m4a backend + m4b frontend) đã ship. m4a: content parser + REST + cache + Swagger + RBAC.
> m4b: Next.js 14 frontend (auth + question browser). Các nhóm SRS / Quiz / Mock Interview / Code Challenge /
> Notes / Blog / Agent / Dashboard / Notification / Export / WebSocket là **phase sau** — giữ ở đây làm thiết kế, **chưa** implement.

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

```
POST   /srs/enroll              Enroll questions into SRS deck
       Body:    { questionIds[], deckId? }
       Returns: 201 { enrolled: int, cardIds[] }

GET    /srs/due                 Get cards due for review
       Query:   ?moduleId=&limit=
       Returns: List<SRSCardDTO>

POST   /srs/review/{cardId}     Submit review result
       Body:    { quality: 0-3, timeMs }
       Returns: { nextReview, interval, easeFactor }

GET    /srs/stats               Get SRS statistics
       Returns: { dueToday, learned, mature, young }

POST   /srs/reset               Reset all SRS cards
```

## Quiz Endpoints

```
POST   /quiz/generate           Generate quiz
       Body:    { moduleId, count, strategy, difficulty }
       strategy: "RANDOM" | "WEAKNESS" | "INTERVIEW" | "SPACED"
       Returns: Quiz { id, questions[], timeLimit }

POST   /quiz/{id}/submit        Submit quiz answers
       Body:    { answers: [{ questionId, selectedOptionId, text? }] }
       Returns: QuizResult { score, correctCount, breakdown[] }

GET    /quiz/{id}               Get quiz detail

GET    /quiz/history            Get quiz history
       Query:   ?page=&size=
```

## Mock Interview Endpoints

```
POST   /mock-interview/start    Start session
       Body:    { topicId, questionCount, mode: "TEXT" | "AUDIO" }
       Returns: Session { id, questions[], timeLimit }

POST   /mock-interview/{id}/answer  Submit answer
       Body:    { questionId, answerText }
       Returns: GradingResult { score, feedback, sampleAnswer }

POST   /mock-interview/{id}/finish  Finish and get report

GET    /mock-interview/history  Get past mock interviews
```

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

PATCH  /admin/content/questions/{id}   Update question (ADMIN) (m4a)
       Body:    { title?, answerHtml?, difficulty?, tags? }  (field null = giữ nguyên)
       Returns: 200 AdminQuestionDTO
       Errors:  404 nếu id không tồn tại
       Note:    KHÔNG đổi được `sortOrder` (không có field này trong update request).
                Khi đổi title/answer/tags → tính lại `searchKeywords` (searchable_text)

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
