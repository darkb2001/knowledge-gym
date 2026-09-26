# Notes System — Ghi chú cá nhân cho người học

## UX Flow

```
┌─────────────────────────────────────────────────────┐
│  Q1: Phân biệt JDK, JRE và JVM?                    │
│                                                     │
│  [Answer section...]                                │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │ 📝 Ghi chú của bạn                         │    │
│  │ ┌─────────────────────────────────────────┐ │    │
│  │ │ Mình hay nhầm JRE vs JDK.               │ │    │
│  │ │ Nhớ: JDK = JRE + tools (javac, jdb)     │ │    │
│  │ │                                         │ │    │
│  │ │ ✏️ Markdown editor                       │ │    │
│  │ └─────────────────────────────────────────┘ │    │
│  │ [Lưu]  [Tạo flashcard từ note]  [Share]   │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

## Kiểu ghi chú

| Type | Mô tả | Use case |
|------|-------|----------|
| **Quick Note** | Ghi chú gắn liền 1 câu hỏi | "Mẹo nhớ: JDK = JRE + tools" |
| **Study Note** | Note dài, markdown, gắn 1 module | Tóm tắt toàn bộ Java Core |
| **Highlight** | Highlight text trong answer + annotation | Đánh dấu phần quan trọng |
| **Bookmark** | Đánh dấu câu hỏi hay | "Ôn lại trước phỏng vấn" |

> **Tags không phải note_type.** Mọi note đều có `tags TEXT[]` (searchable, vd. `#interview #spring`).
> `note_type` chỉ có 4 giá trị: `QUICK` / `STUDY` / `HIGHLIGHT` / `BOOKMARK`.

## DB Schema

```sql
notes
├── id (UUID)
├── user_id → users
├── question_id → questions (nullable — có thể note ở module level)
├── module_id → modules (nullable)
├── note_type (QUICK | STUDY | HIGHLIGHT | BOOKMARK)
├── content (TEXT, markdown)
├── highlight_range (JSONB — start/end offset nếu highlight)
├── tags (TEXT[] + GIN index)
├── search_vector (tsvector + GIN — fallback khi de-scope Elasticsearch)
├── is_public (BOOLEAN — share được không)
├── created_at, updated_at
```

**Truy vết note → nội dung khác** (xem `07-erd.md`):
- `srs_cards.source_note_id` → note nào tạo ra flashcard này
- `blog_posts.source_note_id` → note nào được AI biến thành blog post

## Notes Dashboard

```
┌─────────────────────────────────────────────────┐
│  My Notes Dashboard                             │
│                                                 │
│  📁 All Notes (24)                              │
│  ├── 📌 Bookmarked (8)                          │
│  ├── 📝 Quick Notes (10)                        │
│  └── 📖 Study Notes (6)                         │
│                                                 │
│  Gần đây:                                       │
│  ┌────────────────────────────────────────────┐ │
│  │ 📝 Q3: equals/hashCode contract           │ │
│  │ "Nhớ override cả hai, dùng Lombok..."     │ │
│  │ #java #collections       2 giờ trước      │ │
│  ├────────────────────────────────────────────┤ │
│  │ 📖 Module 5: Database tóm tắt             │ │
│  │ "ACID = Atomic, Consistent, Isolated..."  │ │
│  │ #database #acid          Hôm qua          │ │
│  └────────────────────────────────────────────┘ │
│                                                 │
│  [Tạo Note mới]  [Export PDF]  [Import]        │
└─────────────────────────────────────────────────┘
```

## Tính năng đặc biệt

| Feature | Mô tả |
|---------|-------|
| **Note → Flashcard** | Chuyển ghi chú thành flashcard chỉ bằng 1 click |
| **Search across notes** | Full-text search trên tất cả notes |
| **Export** | Export all notes → PDF/Markdown (mang đi phỏng vấn) |
| **Tag analytics** | "Bạn hay note về #spring_boot nhất" → gợi ý ôn |
| **AI summarize** | Ghi chú dài → AI tóm tắt thành flashcard |

## Notes ↔ Blog ↔ Learning Integration

```
┌─────────────────────────────────────────────────────┐
│                Knowledge Flywheel                    │
│                                                     │
│  Học → Notes → Blog → SEO → Users → Học            │
│                                                     │
│  ┌──────┐     ┌──────┐     ┌──────┐                │
│  │Study │────▶│Note  │────▶│Blog  │                │
│  │      │     │      │     │      │                │
│  └──────┘     └──────┘     └──────┘                │
│     ▲                              │                │
│     │                              ▼                │
│  ┌──────┐                      ┌──────┐            │
│  │New   │◀─────────────────────│SEO + │            │
│  │Users │                      │Social│            │
│  └──────┘                      └──────┘            │
│                                                     │
│  Notes hay của user → AI biến thành blog post      │
│  Blog hay → thu hút user mới → học → ghi note      │
└─────────────────────────────────────────────────────┘
```
