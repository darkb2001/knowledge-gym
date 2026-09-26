# REST API Design — Knowledge Gym

> Base URL: `/api/v1`  
> Auth: JWT Bearer (access) + refresh httpOnly cookie  
> Total: ~75 endpoints  
> **ADR-002:** single-tenant — **không** có field/param `tenant`. Enum strategy/status = **UPPERCASE**.  
> Schema: `07-erd.md` + DDL `07-erd-ddl.sql`. Bookmark = `notes.note_type = BOOKMARK`.

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

GET    /auth/oauth2/authorization/google   OAuth2 Google login (redirect sang Google)
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

```
GET    /topics                  List all topics
       Returns: List<TopicDTO>

GET    /topics/{slug}           Get topic detail with modules
       Returns: { topic, modules[] }

GET    /modules                 List modules (filter by topicId)
       Query:   ?topicId=&page=&size=
       Returns: Page<ModuleDTO>

GET    /modules/{id}            Get module detail
       Returns: { module, questions[], stats }

GET    /modules/{id}/mindmap    Get mindmap structure
       Returns: { nodes[], edges[] }
```

## Questions Endpoints

```
GET    /questions               Search & filter questions
       Query:   ?moduleId=&tag=&difficulty=&q=&page=&size=
       Returns: Page<QuestionDTO>

GET    /questions/{id}          Get single question with full answer
       Returns: { id, title, answerHtml, options[], tags }

POST   /questions               Create question (ADMIN)
       Body:    { moduleId, title, answerHtml, difficulty, tags, options }

PATCH  /questions/{id}          Update question (ADMIN)

DELETE /questions/{id}          Delete question (ADMIN)

POST   /questions/{id}/bookmark Toggle bookmark
       Impl:    UPSERT/DELETE notes row note_type=BOOKMARK for (user, question)
       Returns: 200 { bookmarked: boolean }

GET    /questions/random        Get random question
       Query:   ?moduleId=&difficulty=&excludeIds=
       Returns: QuestionDTO
```

## Global Search

```
GET    /search                  Global search (questions + notes + blog)
       Query:   ?q=&types=questions,notes,blog&page=&size=
       Impl:    Elasticsearch primary; fallback PG tsvector (de-scope ladder #7)
       Returns: { hits: [{ type, id, title, snippet, score }] }
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

```
GET    /admin/users             List users (paginated)

PATCH  /admin/users/{id}/role   Change user role

GET    /admin/stats             Platform-wide stats

GET    /admin/audit-logs        View audit logs

POST   /admin/content/import     Import from JSON/HTML

POST   /admin/content/parse     Parse docs/ folder
```

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
