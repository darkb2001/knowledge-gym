# Auto-Blog Engine — AI Writer và blog tri thức

## Architecture

M9 Collector → normalized PostgreSQL references → M10 topic/template selection → dedicated text model API → source-ID validation + HTML sanitization → immutable draft revisions → manual review or qualified auto-publish → M9 blog/search/RSS.

**Implementation boundary:** collector and manual publishing are M9 and do not call an LLM. AI drafting starts in M10. The owner controls the daily schedule separately from the publish policy: manual mode provides a sanitized preview and iterative AI revision before explicit publish; qualified auto-publish mode publishes only when hard validation and the configured threshold pass, otherwise the draft goes to `REVIEW`. The user's Hermes Agent on Proxmox is an optional external admin/ops interface, not the writer itself. If connected, it uses typed commands through a private, authenticated application API; do not expose its general tool-enabled API to product requests or pass untrusted collected text as agent instructions.

## Blog Content Types

| Type | Tần suất | Mô tả |
|------|----------|-------|
| **Daily Deep Dive** | Hàng ngày | Mổ xẻ một chủ đề dựa trên nguồn tri thức đã thu thập |
| **Weekly Digest** | Hàng tuần | "Top 10 câu hỏi Java tuần này" |
| **Comparison Post** | 2 lần/tuần | "HashMap vs ConcurrentHashMap" |
| **Cheat Sheet** | Hàng tuần | "Spring Transaction Cheat Sheet" |
| **Interview Story** | Khi có | Kịch bản phỏng vấn mẫu |
| **Code Snippet** | Hàng ngày | 1 đoạn code hay + giải thích |

## Blog Templates

| Template | Khi nào dùng | Structure |
|----------|--------------|-----------|
| **Deep Dive** | 1 câu hỏi phỏng vấn chi tiết | What → Why → How → Code → Diagram → Pitfall → Interview Tips |
| **Comparison** | 2-3 thứ cần so sánh | Overview → Feature table → Code comparison → When to use → Verdict |
| **Tutorial** | Hướng dẫn thực hành | Problem → Solution → Step-by-step code → Result → Extensions |
| **News Analysis** | Phân tích trend/release | What happened → Why matters → Impact → Code example → Action items |
| **Cheat Sheet** | Tổng hợp nhanh | Categorized table → Code snippets → Tips → Download PDF |
| **Interview Prep** | Chuẩn bị phỏng vấn | Top N questions → Answers → Code → Common mistakes |

## AI Writer Pipeline

The model receives up to eight collected references as quoted, untrusted evidence. Private user notes are excluded. It returns structured fields and source IDs; the application rejects IDs outside the supplied set, strips model-created links, sanitizes HTML, and appends canonical source links itself. The quality score checks editorial signals and evidence coverage; it does not establish factual accuracy.

`OPENAI_API_KEY` is read only by the backend provider adapter and is never returned to the browser. The adapter sends text-only Chat Completions requests with tools disabled. Resilience4j applies a circuit breaker, up to three attempts, and a global rate limiter. PostgreSQL reserves daily request/token/cost budgets before provider calls and records reported usage afterward. Configure the provider account's spending limit as a second cap.

For a deployment, set `OPENAI_API_KEY` as an application secret and set `APP_BLOG_WRITER_ENABLED=true` to start the queue worker. The persisted schedule itself defaults off and the publication policy defaults to `MANUAL_REVIEW`. Without the key, the admin page reports the missing backend configuration and generation/revision requests are rejected; the queue worker also stays idle. The key must never be copied into browser environment variables.

## Scheduling (Cron)

The times below are example defaults, not hard-coded schedules. In M10 the owner enables/disables the daily writer and selects local time/timezone in the admin UI; scheduled and manual runs enter the same idempotent queue. The publication policy is a separate setting: manual mode creates a previewable draft for iterative revision and approval; qualified auto-publish publishes only after validation and threshold checks, otherwise it enters review.

At the configured local time, M10 enqueues a daily idempotent run. `Generate now` enters the same queue and obeys the same budget. `MANUAL_REVIEW` (default) is independent from the scheduler toggle and provides a sanitized preview, manual edits, AI revisions, version compare/restore, explicit publish, and reject. `AUTO_PUBLISH_QUALIFIED` publishes only after hard checks and the configured threshold pass; otherwise the post remains in review. The app rereads policy after generation so a switch to manual prevents in-flight auto-publish.

## Blog UI

```
┌─────────────────────────────────────────────────────────────┐
│  📰 Knowledge Gym Blog                                      │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  🔥 Hôm nay                                          │    │
│  │                                                      │    │
│  │  HashMap internals: Hiểu sâu để phỏng vấn giỏi      │    │
│  │  Bởi AI Agent · 24/09/2026 · 5 phút đọc             │    │
│  │                                                      │    │
│  │  [Đọc tiếp →]  💬 12 comments  ⭐ 45 likes          │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  [🏷 Tags]  [📅 Archive]  [🔍 Search]  [📡 RSS]            │
└─────────────────────────────────────────────────────────────┘
```

## DB Schema

```sql
blog_posts
├── id, title, slug (unique), body (TEXT), excerpt
├── cover_image_url (Garage S3)
├── source_module_id → modules (nullable)
├── source_question_id → questions (nullable)
├── source_note_id → notes (nullable — note nào biến thành blog)
├── author_type (AI | HUMAN)
├── author_id → users (nullable — NULL khi AI viết)
├── quality_score (DECIMAL)
├── seo_title, seo_description, seo_keywords
├── status (DRAFT | REVIEW | PUBLISHED | ARCHIVED)
├── published_at
├── view_count, like_count   ← CACHE, nguồn thật là blog_views / blog_post_likes
├── ai_model, ai_prompt_used
├── tags (TEXT[])
├── created_at, updated_at

blog_comments
├── id, post_id → blog_posts, user_id → users
├── parent_id → blog_comments (nullable, nested replies)
├── content (TEXT), created_at

blog_views                    ← view tracking idempotent
├── id, post_id → blog_posts, user_id → users
├── UNIQUE (post_id, user_id)
├── viewed_at, read_time_sec

blog_post_likes               ← like/unlike idempotent
├── id, post_id → blog_posts, user_id → users
├── UNIQUE (post_id, user_id)
├── created_at

blog_generation_queue
├── id, topic, angle, priority
├── selection_strategy (TRENDING | GAP | SEASONAL | DEMAND)  ← chọn topic nào
├── writer_strategy (AUTO | TEMPLATE | CURATED)              ← viết kiểu gì
├── reference_items (JSONB)
├── status (QUEUED | GENERATING | DONE | FAILED)
├── idempotency_key (unique), requested_by, attempts, retry_after
├── scheduled_for, started_at, completed_at, generated_post_id, error_message
   ⚠ tokens_used / cost_usd KHÔNG ở đây — chỉ ở agent_runs

agent_runs
├── id, agent_type (COLLECTOR | WRITER | PUBLISHER)
├── queue_id → blog_generation_queue (nullable)
├── status (RUNNING | SUCCESS | FAILED)
├── started_at, finished_at
├── input_summary, output_summary, error_message
├── tokens_used, cost_usd     ← nguồn duy nhất cho AI cost

blog_writer_settings           ← single-row persistent config (V022)
├── schedule_enabled, local_time, timezone, daily_limit
├── publish_policy (MANUAL_REVIEW | AUTO_PUBLISH_QUALIFIED), quality_threshold
├── last_scheduled_date, last_run_at, updated_by, updated_at

blog_draft_revisions           ← append-only versions (V022)
├── post_id, version, title/body/excerpt, SEO metadata
├── source_ids, instruction, model, quality_score, tokens_used, cost_usd, created_by

blog_writer_audit              ← policy/settings change history (V022)
blog_ai_budget_reservations    ← atomic daily request/token/cost caps (V022)
```

**Lifecycle rõ ràng:** `blog_generation_queue.status` kết thúc ở `DONE`/`FAILED`; ba lỗi provider sẽ chuyển queue sang `FAILED`, còn transient failures được retry có backoff. Vòng đời bài viết (review/publish/reject-as-archived) nằm trên `blog_posts.status`. `agent_runs` ghi usage theo lần gọi; revision lưu lại cùng usage gắn với phiên bản bất biến.

## Cost Tracking

Usage and estimated provider cost are recorded per generation/revision in `agent_runs` and immutable revisions. The configurable defaults currently match the provider's published GPT-4o mini rates ([model page](https://developers.openai.com/api/docs/models/gpt-4o-mini)); update the environment values if the model or provider rates change.

## Agent Dashboard (Admin)

```
┌─────────────────────────────────────────────────────┐
│  Blog Agent Dashboard                               │
│                                                     │
│  📊 Stats: 47 published, 3 in review               │
│  Avg views/post: 234                                │
│                                                     │
│  ⚙️ Schedule: Daily 6AM, Weekly Mon 8AM             │
│  Auto-publish threshold: 85%                        │
│                                                     │
│  📝 Review Queue                                    │
│  1. "ConcurrentHashMap..." (72%) [Approve] [Reject] │
│  2. "JPA N+1..." (81%) [Approve] [Reject]          │
│                                                     │
│  [▶ Run now]  [Pause]  [Settings]                   │
└─────────────────────────────────────────────────────┘
```
