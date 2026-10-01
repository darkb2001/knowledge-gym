# Auto-Blog Engine — AI Agent viết blog hàng ngày

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Auto-Blog Pipeline                        │
│                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌───────┐ │
│  │ Content  │───▶│  AI      │───▶│ Review   │───▶│Publish│ │
│  │ Selector │    │  Writer  │    │ Queue    │    │       │ │
│  └──────────┘    └──────────┘    └──────────┘    └───────┘ │
│       │               │               │               │     │
│  Pick topic      Generate post    Human approve    RSS/SEO  │
│  from DB         from knowledge   (optional)       Social   │
└─────────────────────────────────────────────────────────────┘
```

**Implementation boundary:** collector and manual publishing are M9 and do not call an LLM. AI drafting starts in M10. The owner controls the daily schedule separately from the publish policy: manual mode provides a sanitized preview and iterative AI revision before explicit publish; qualified auto-publish mode publishes only when hard validation and the configured threshold pass, otherwise the draft goes to `REVIEW`. The user's Hermes Agent on Proxmox is an optional external admin/ops interface, not the writer itself. If connected, it uses typed commands through a private, authenticated application API; do not expose its general tool-enabled API to product requests or pass untrusted collected text as agent instructions.

## Blog Content Types

| Type | Tần suất | Mô tả |
|------|----------|-------|
| **Daily Deep Dive** | Hàng ngày | Mổ xẻ 1 câu hỏi phỏng vấn chi tiết |
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

```python
class BlogWriterAgent:
    def write_post(self, topic, reference_data, collected_items):
        # 1. Gather context: knowledge base + internet + user notes
        context = self.gather_context(topic, ...)
        
        # 2. Choose template
        template = self.choose_template(topic, context)
        
        # 3. AI generate
        draft = self.ai_generate(
            model="gpt-4o-mini",
            prompt=self.build_prompt(template, context),
            max_tokens=4000,
            temperature=0.7
        )
        
        # 4. Enrich: code examples + Mermaid diagrams
        draft = self.add_code_examples(draft, context.code_snippets)
        draft = self.generate_mermaid_diagrams(draft)
        
        # 5. SEO optimization
        draft = self.optimize_seo(draft, ...)
        
        # 6. Quality score
        score = self.evaluate_quality(draft, criteria={
            "accuracy", "readability", "originality",
            "code_quality", "seo_score", "length"
        })
        
        return Post(
            status="published" if score > 85 else "review"
        )
```

## Scheduling (Cron)

The times below are example defaults, not hard-coded schedules. In M10 the owner enables/disables the daily writer and selects local time/timezone in the admin UI; scheduled and manual runs enter the same idempotent queue. The publication policy is a separate setting: manual mode creates a previewable draft for iterative revision and approval; qualified auto-publish publishes only after validation and threshold checks, otherwise it enters review.

```
*/6 * * * *    Collector.run()         # Mỗi 6 giờ
0 2 * * *      TopicSelector.select()  # 2 AM
0 3 * * *      BlogWriter.write()      # 3 AM
0 4 * * *      Publisher.publish()     # 4 AM
0 5 * * 1      WeeklyDigest.generate() # 5 AM Monday
0 6 * * *      SocialPoster.post()     # 6 AM
0 7 * * *      EmailDigest.send()      # 7 AM
```

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
├── scheduled_for, error_message
   ⚠ tokens_used / cost_usd KHÔNG ở đây — chỉ ở agent_runs

agent_runs
├── id, agent_type (COLLECTOR | WRITER | PUBLISHER)
├── queue_id → blog_generation_queue (nullable)
├── status (RUNNING | SUCCESS | FAILED)
├── started_at, finished_at
├── input_summary, output_summary, error_message
├── tokens_used, cost_usd     ← nguồn duy nhất cho AI cost
```

**Lifecycle rõ ràng:** `blog_generation_queue.status` kết thúc ở `DONE`/`FAILED`.
Vòng đời bài viết (review/publish) nằm trên `blog_posts.status`.

## Cost Optimization

| Action | Model | Cost/bài |
|--------|-------|----------|
| Topic selection | Local logic | $0 |
| Research enrichment | GPT-4o-mini | ~$0.02 |
| Draft writing | GPT-4o-mini | ~$0.08 |
| Quality review | Claude 3.5 Haiku | ~$0.01 |
| Cover image | DALL-E 3 | ~$0.04 |
| SEO optimization | GPT-4o-mini | ~$0.01 |
| **Total/bài** | | **~$0.16** (~$5/tháng) |

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
