# ERD — Knowledge Gym (32 bảng PostgreSQL + 1 MVIEW + Redis)

> **ADR-002:** Single-tenant — **không** có bảng `tenants`. Multi-tenant/monetization = **deferred Phase 5+** (xem `02-monetization.md`).
> **Diagram:** `docs/07-erd.mmd` (compact) · `docs/07-erd.svg` · `docs/07-erd-preview.html`.
> **DDL (cook source of truth):** [`docs/07-erd-ddl.sql`](./07-erd-ddl.sql) — full `CREATE TABLE` V001–V013. File này = lookup + tiers + quy tắc.
> Preview IDE: Command Palette → **Mermaid Viewer: Open Preview to the Side**.
> **Quy tắc:** mọi bảng **phải** thuộc đúng 1 migration. Enum **luôn UPPERCASE**. FK nullable ghi rõ.

## Quy tắc thiết kế (đọc trước khi implement)

| Quy tắc | Nội dung |
|---|---|
| **Enum casing** | **UPPERCASE** ở cả DB (CHECK constraint / VARCHAR) và Java enum. Không dùng lowercase trong docs. |
| **Naming FK** | Luôn có hậu tố `_id`: `used_in_post_id`, `source_module_id`, `deck_id`. |
| **Soft ref** | FK nullable + `ON DELETE SET NULL` cho tham chiếu không bắt buộc (`source_note_id`, `deck_id`). |
| **Counter cache** | `blog_posts.view_count` / `like_count` là **cache** — nguồn thật là `blog_views` / `blog_post_likes` (+ Redis buffer 5m). |
| **XP / streak** | **PostgreSQL là source of truth** (`users.xp`, `user_progress.streak_days`). Redis (`lb:global`, `streak:*`) chỉ là cache TTL 1h. |
| **Attempt tables** | 3 bảng riêng có mục đích khác nhau — **không gộp**: `study_attempts` (flashcard/daily/practice), `quiz_answers` (trong quiz session), `interview_answers` (trong interview session). |
| **Search** | Elasticsearch là chính. `search_vector tsvector + GIN` trên `questions`/`notes` là **fallback** khi de-scope ES (xem de-scope ladder). |
| **Tags** | `TEXT[] + GIN index`. **Không** dùng junction table `question_tags`. |
| **Reserved enum** | `users.role` có `PREMIUM`, `users.auth_provider` có `GITHUB` — khai báo sẵn trong CHECK, **chưa dùng** ở MVP. |
| **OAuth columns** | `auth_provider` (`LOCAL`/`GOOGLE`/`GITHUB`) + `oauth_id` (NULL khi LOCAL). **Không** có cột `oauth_provider` (trùng nghĩa). UK partial: `(auth_provider, oauth_id) WHERE oauth_id IS NOT NULL`. |

## Bảng tra cứu — 32 bảng × migration

### Identity (6 bảng)

| Bảng | Migration | PK / UK | Ghi chú |
|---|---|---|---|
| `users` | **V001** | `id PK`, `email UK`, `UK (auth_provider, oauth_id)` partial | `password_hash` NULL khi OAuth-only. `auth_provider` + `oauth_id` (không có `oauth_provider`). `xp INT DEFAULT 0` = source of truth |
| `refresh_tokens` | **V001** | `id PK`, `token_hash UK` | Audit log 2 lớp (Redis lookup + PG audit). `expires_at` TTL 7d |
| `notification_preferences` | **V001** | `id PK`, `UK (user_id, channel)` | 1 user nhiều channel (EMAIL + PUSH) |
| `audit_logs` | **V008** | `id PK` | `details JSONB` |
| `user_badges` | **V008** | `PK (user_id, badge_code)` | `badge_code` ENUM, không cần bảng lookup. Gamification = m12, de-scopable |
| `password_reset_codes` | **V011** | `id PK` | Mã 6 ký tự, `code_hash` SHA-256, TTL 10m, max 5 attempts |

### Content (4 bảng)

| Bảng | Migration | PK / UK | Ghi chú |
|---|---|---|---|
| `topics` | **V002** | `id PK`, `slug UK` | |
| `modules` | **V002** | `id PK`, `slug UK` | FK → `topics` |
| `questions` | **V003** | `id PK` | `tags TEXT[] + GIN`, `search_vector tsvector + GIN` (ES fallback), `hints JSONB` nullable, `version INT` cho `@Version` |
| `question_options` | **V003** | `id PK` | FK → `questions`, multiple-choice |

### Learning (13 bảng)

| Bảng | Migration | PK / UK | Ghi chú |
|---|---|---|---|
| `srs_decks` | **V004** | `id PK` | `module_id` NULL = custom deck do user tạo (`01-ux-modes.md` L42–43) |
| `srs_cards` | **V004** | `id PK`, `UK (user_id, question_id)` | `deck_id` NULL, `source_note_id` NULL (note→card), SM-2 fields |
| `study_attempts` | **V004** | `id PK` | `source (FLASHCARD/DAILY/PRACTICE)` — phân biệt nguồn attempt |
| `user_progress` | **V004** | `id PK`, `UK (user_id, module_id)` | UPSERT conflict target. `streak_days` per-module |
| `challenges` | **V012** | `id PK`, `slug UK` | Code sandbox — **khác** `questions`. `hints JSONB`, `solution_code` |
| `challenge_test_cases` | **V012** | `id PK` | FK → `challenges`, `is_hidden` cho hidden test |
| `code_submissions` | **V012** | `id PK` | FK → **`challenges`** (không phải `questions`), `viewed_solution_at` |
| `quiz_sessions` | **V013** | `id PK` | `strategy (RANDOM/WEAKNESS/INTERVIEW/SPACED)` |
| `quiz_answers` | **V013** | `id PK` | `selected_option_id` FK NULL + `answer_text` (hỗ trợ cả MCQ và text) |
| `interview_sessions` | **V013** | `id PK` | `mode (TEXT/AUDIO)`, `status (ACTIVE/FINISHED)`, `overall_score` |
| `interview_answers` | **V013** | `id PK` | Tách khỏi session (1 session → N câu). `audio_url` cho speech mode |
| `notifications` | **V013** | `id PK` | `metadata JSONB` chứa entity refs (vd. `questionId`) |
| `daily_challenge_assignments` | **V013** | `id PK`, `UK (user_id, challenge_date)` | `status (PENDING/COMPLETED/SKIPPED)` — nút "Skip hôm nay" |

### Notes (1 bảng)

| Bảng | Migration | PK / UK | Ghi chú |
|---|---|---|---|
| `notes` | **V005** | `id PK` | `note_type (QUICK/STUDY/HIGHLIGHT/BOOKMARK)` — bookmark = note row. `search_vector` ES fallback |

### Blog Agent (8 bảng)

| Bảng | Migration | PK / UK | Ghi chú |
|---|---|---|---|
| `blog_posts` | **V006** | `id PK`, `slug UK` | `author_id` NULL khi AI viết. `source_module_id` / `source_question_id` / `source_note_id` đều nullable |
| `blog_comments` | **V006** | `id PK` | `parent_id` NULL cho replies (self-FK) |
| `blog_views` | **V006** | `id PK`, `UK (post_id, user_id)` | Idempotent view tracking |
| `blog_post_likes` | **V006** | `id PK`, `UK (post_id, user_id)` | Like/unlike idempotent — `like_count` chỉ là cache |
| `collector_sources` | **V007** | `id PK` | `fetch_interval_sec INT`, `config JSONB` |
| `collected_items` | **V007** | `id PK`, `url UK` | `content_hash` dedup, `used_in_post_id` FK NULL |
| `blog_generation_queue` | **V007** | `id PK` | `selection_strategy` (chọn topic) **tách khỏi** `writer_strategy` (cách viết) |
| `agent_runs` | **V007** | `id PK` | `queue_id` FK NULL. `tokens_used` / `cost_usd` **chỉ ở đây** (không duplicate trên queue) |

## Flyway Migrations — mọi bảng có đúng 1 migration

```
V001__create_users.sql               users, refresh_tokens, notification_preferences        (3)
V002__create_topics_modules.sql      topics, modules                                        (2)
V003__create_questions.sql           questions, question_options                            (2)
V004__create_srs_progress.sql        srs_decks, srs_cards, study_attempts, user_progress    (4)
V005__create_notes.sql               notes                                                  (1)
V006__create_blog.sql                blog_posts, blog_comments, blog_views,
                                     blog_post_likes                                        (4)
V007__create_agent.sql               collector_sources, collected_items,
                                     blog_generation_queue, agent_runs                      (4)
V008__create_audit.sql               audit_logs, user_badges                                (2)
V009__create_indexes.sql             composite + partial indexes (không tạo bảng)           (0)
V010__create_mviews.sql              user_topic_mastery MATERIALIZED VIEW                    (0)
V011__password_reset_codes.sql       password_reset_codes                                   (1)
V012__create_challenges.sql          challenges, challenge_test_cases, code_submissions     (3)
V013__create_quiz_interview.sql      quiz_sessions, quiz_answers, interview_sessions,
                                     interview_answers, notifications,
                                     daily_challenge_assignments                            (6)
                                                                            ────────────────────
                                                                            TOTAL:        32 bảng
```

**Kiểm tra:** 3+2+2+4+1+4+4+2+0+0+1+3+6 = **32** ✓ — không bảng nào thiếu migration, không migration nào trùng số.

## Index chính (V009)

```sql
-- Auth
CREATE INDEX idx_refresh_tokens_user     ON refresh_tokens(user_id) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX idx_users_oauth      ON users(auth_provider, oauth_id)
                                         WHERE oauth_id IS NOT NULL;
-- SRS (query nóng nhất: "cards due today")
CREATE INDEX idx_srs_due                 ON srs_cards(user_id, next_review);
-- Dashboard heatmap + weakness radar
CREATE INDEX idx_attempts_user_date      ON study_attempts(user_id, attempted_at DESC);
CREATE INDEX idx_attempts_wrong          ON study_attempts(question_id)
                                         WHERE is_correct = false;
-- Leaderboard
CREATE INDEX idx_users_xp                ON users(xp DESC);
-- Notifications
CREATE INDEX idx_notif_unread            ON notifications(user_id, created_at DESC)
                                         WHERE read = false;
-- Daily challenge lookup
CREATE INDEX idx_daily_user_date         ON daily_challenge_assignments(user_id, challenge_date DESC);
-- Search fallback (khi de-scope Elasticsearch)
CREATE INDEX idx_questions_search        ON questions USING GIN(search_vector);
CREATE INDEX idx_notes_search            ON notes USING GIN(search_vector);
CREATE INDEX idx_questions_tags          ON questions USING GIN(tags);
-- Blog
CREATE INDEX idx_blog_published          ON blog_posts(published_at DESC)
                                         WHERE status = 'PUBLISHED';
```

## Redis — cache layer (không phải bảng PostgreSQL)

```
┌─ Redis 7 — Knowledge Gym Key Space ────────────────────────────────────┐
│  AUTH (m03)           │  CACHE (m04)           │  RATE LIMIT (m03)     │
│  rt:{hash}     → 7d   │  cache:{entity}:{id}   │  rl:login:{ip}       │
│  rt:blacklist:{hash}  │    → JSON, 30-60m      │  rl:register:{ip}    │
│                → 7d   │                        │  rl:forgot:{ip}      │
│  rt:revoked:{fid}→7d  │                        │  rl:global:{ip}     │
│  pwd_reset:{email}→10m│                        │                      │
│───────────────────────┼────────────────────────┼───────────────────────│
│  LEADERBOARD (m07)    │  STREAK (m07)          │  AI QUOTA (m10)      │
│  lb:global     → 1h   │  streak:{uid}:cur      │  ai:{uid}:{date}    │
│  lb:topic:{id} → 1h   │  streak:{uid}:last     │    → 48h            │
│  ↑ cache của users.xp │  ↑ cache của           │                      │
│                        │   user_progress        │                      │
│───────────────────────┼────────────────────────┼───────────────────────│
│  BLOG (m09)           │  LOCKS (m06,m10)       │  ONLINE (m12)        │
│  views:{pid}   → 5m   │  lock:quiz:{sid}:{uid} │  online:{uid} → 5m  │
│  likes:{pid}   → 5m   │  lock:ai:writer → 120s │  online:count (HLL) │
│  ↑ buffer, flush 5m   │                        │                      │
│   → blog_views /       │                        │                      │
│     blog_post_likes    │                        │                      │
└────────────────────────────────────────────────────────────────────────┘

Estimated memory (1000 users): ~6 MB  ← Redis 256 MB trên Docker là đủ
```

**Refresh token = 2 lớp:**
- **Redis** (`rt:{hash}`) — lookup O(1) khi refresh, TTL tự hết hạn
- **PostgreSQL** (`refresh_tokens`) — audit log + reuse detection family (không mất khi Redis restart)

## MVP schema vs Full schema

> **Flyway vẫn tạo đủ 32 bảng ở mini-phase 2** (tránh migration lẻ về sau + `ddl-auto=validate` ổn định).
> Bảng dưới phân theo **feature nào được phép dùng / ship** — không phải “tạo bảng sau”.

| Tier | Mini-phases | # bảng | Ý nghĩa |
|---|---|---|---|
| **MVP Must** | m1–m7 | **16** | App usable: auth + content + SRS + quiz/interview + dashboard |
| **MVP Optional** | m6 (de-scope ladder #2) | **3** | Code sandbox — có bảng sẵn, feature có thể defer |
| **Full** | m8–m12 | **13** | Notes, blog agent, daily challenge, audit/badges |
| **Deferred Phase 5+** | — | **~9** | Monetization / multi-tenant — **không** nằm trong 32 |

### MVP Must (16) — m1–m7

| Context | Bảng | Phase dùng |
|---|---|---|
| Identity | `users`, `refresh_tokens`, `password_reset_codes`, `notification_preferences` | m3 |
| Content | `topics`, `modules`, `questions`, `question_options` | m4 |
| Learning | `srs_decks`, `srs_cards`, `study_attempts`, `user_progress` | m5, m7 |
| Learning | `quiz_sessions`, `quiz_answers`, `interview_sessions`, `interview_answers` | m6 |

### MVP Optional / de-scopeable (3) — m6

| Bảng | Ghi chú |
|---|---|
| `challenges`, `challenge_test_cases`, `code_submissions` | Code sandbox Docker — cắt theo de-scope ladder #2 nếu trễ |

### Full (13) — m8–m12

| Context | Bảng | Phase dùng |
|---|---|---|
| Notes | `notes` | m8 |
| Blog | `blog_posts`, `blog_comments`, `blog_views`, `blog_post_likes` | m9 |
| Agent | `collector_sources`, `collected_items`, `blog_generation_queue`, `agent_runs` | m9–m10 |
| Polish | `notifications`, `daily_challenge_assignments` | m12 |
| Polish | `audit_logs`, `user_badges` | m12 (badges = de-scope ladder #4) |

**Kiểm tra:** 16 + 3 + 13 = **32** ✓

### Quy tắc ship

1. **m7 xong = MVP ship được** dù Full tables trống (0 rows), không wire API.
2. De-scope ladder cắt **feature/code**, không drop bảng đã migrate (tránh Flyway chaos).
3. JPA entity Full có thể tạo ở m2 (skeleton) hoặc đúng phase — miễn `ddl-auto=validate` khớp schema.

## Deferred Phase 5+ — KHÔNG có trong 32 bảng

Các feature trong `02-monetization.md` **cố tình không** đưa vào ERD (ADR-002):

| Feature | Bảng sẽ cần (Phase 5+) | Lý do defer |
|---|---|---|
| Multi-tenant / white-label | `tenants`, `tenant_settings`, `tenant_id` trên mọi bảng | ADR-002: single-tenant. Thêm sau = migration lớn nhưng chấp nhận được |
| Subscription / billing | `subscription_plans`, `user_subscriptions`, `payments` | Chưa monetize. `users.role` đã reserve `PREMIUM` |
| Content packs marketplace | `content_packs`, `content_pack_topics`, `user_pack_purchases` | Chưa bán content |
| Team / B2B license | `teams`, `team_members` | Chưa có khách B2B |

**Quyết định:** Full product = **32 bảng**. Monetization ~9 bảng = Phase 5+, không nhét vào MVP/Full hiện tại.

## Changelog

### 09/2026 — Rà soát toàn diện (18 → 29 → 32 bảng)

**Lỗi nghiêm trọng đã sửa:**

| # | Vấn đề | Sửa |
|---|---|---|
| 1 | **`quiz_sessions`, `quiz_answers`, `notifications` không thuộc migration nào** — schema sẽ fail `ddl-auto=validate` | Tạo **V013** chứa 6 bảng |
| 2 | `code_submissions.question_id` FK sai — code challenge ≠ question | Sửa → `challenge_id FK (challenges)` |
| 3 | Thiếu `challenges` + `challenge_test_cases` — API `/challenges/{id}` không có bảng | Thêm 2 bảng (V012) |
| 4 | `interview_sessions` gộp cả answer — API là 1 session → N answers | Tách `interview_answers` (V013) |
| 5 | API `POST /blog/posts/{id}/like` nhưng chỉ có `like_count` — không unlike/idempotent được | Thêm `blog_post_likes` UK (post_id, user_id) |
| 6 | Daily Challenge có nút "Skip hôm nay" nhưng không lưu state | Thêm `daily_challenge_assignments` UK (user_id, challenge_date) |
| 7 | "Custom deck" (`01-ux-modes.md`) không có bảng | Thêm `srs_decks` + `srs_cards.deck_id` nullable |
| 8 | `notification_preferences` UK `user_id` — chặn user có nhiều channel | Sửa → `UK (user_id, channel)` |
| 9 | `blog_views` thiếu PK, không idempotent | Thêm `id PK` + `UK (post_id, user_id)` |
| 10 | `user_progress` thiếu UK → UPSERT không có conflict target | Thêm `UK (user_id, module_id)` |
| 11 | `srs_cards` thiếu UK → user có thể có 2 card cùng question | Thêm `UK (user_id, question_id)` |
| 12 | `user_badges` không có PK | Thêm `PK (user_id, badge_code)` |
| 13 | XP chỉ ở Redis (m07) → mất khi restart | `users.xp` = source of truth, Redis là cache |
| 14 | `blog_generation_queue.strategy` mang 2 nghĩa (chọn topic vs cách viết) | Tách `selection_strategy` + `writer_strategy` |
| 15 | `tokens_used`/`cost_usd` duplicate ở queue và `agent_runs` | Chỉ giữ ở `agent_runs` + thêm `queue_id` FK |
| 16 | Note → flashcard / note → blog không truy vết được | Thêm `srs_cards.source_note_id`, `blog_posts.source_note_id` |
| 17 | ES fallback dùng `tsvector` nhưng không có column | Thêm `search_vector tsvector + GIN` |
| 18 | Enum lowercase/UPPERCASE lẫn lộn giữa docs và ERD | Chuẩn hóa **UPPERCASE** toàn bộ |
| 19 | `fetch_interval` vs `fetch_interval_sec`, `used_in_post` vs `used_in_post_id`, `source_topic` vs `source_module_id` | Chuẩn hóa hậu tố `_id` + đơn vị |
| 20 | `study_attempts` không phân biệt nguồn (flashcard/daily/practice) | Thêm `source` enum |

**Bảng mới (3):** `srs_decks`, `daily_challenge_assignments`, `blog_post_likes`
**Migration mới:** `V013__create_quiz_interview.sql` (6 bảng), `V012` đổi tên → `create_challenges` (3 bảng)
