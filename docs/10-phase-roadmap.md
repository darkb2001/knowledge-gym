# Phase Roadmap — 8 tuần

> ⚠️ **STALE — DO NOT COOK FROM THIS FILE.**
>
> Canonical timeline: [`plans/knowledge-gym/plan.md`](../../plans/knowledge-gym/plan.md) — **12 mini-phases**, MVP m1–m7 / Full m1–m12.
> File này còn mô tả V001–V005 / 4 phases cũ; giữ để tham khảo lịch sử thôi.

## Overview

| Phase | Tuần | Focus | Modules covered |
|-------|------|-------|-----------------|
| **1** | 1-2 | Foundation: Auth + Content + Docker | 01,02,03,04,05,06,09,15 |
| **2** | 3-4 | Learning Core: SRS + Quiz + Dashboard | 11,13 + bổ sung |
| **3** | 5-6 | Blog Agent: Collector + Writer | 01,02,07,13 |
| **4** | 7-8 | Notes + Mindmap + Deploy | 05,09,11,12 |

---

## Phase 1: Foundation (Week 1-2)

### Tasks

```
Week 1: Setup + Auth
├── Day 1-2: Project skeleton
│   ├── Init Gradle (Kotlin DSL) multi-module project + Clean Architecture
│   ├── Setup Spring Boot 3.2 + Java 21
│   ├── Docker Compose (PostgreSQL + Redis)
│   ├── Flyway initial migration
│   └── Module 09 coverage: Docker basics
│
├── Day 3-4: JPA Entities
│   ├── User, Topic, Module, Question entities
│   ├── @MappedSuperclass BaseEntity (Module 04)
│   ├── @EntityListeners Auditing
│   ├── @Version for optimistic locking
│   └── Module 04 coverage: Entity design
│
├── Day 5-7: Spring Security + JWT
│   ├── SecurityConfig with JWT filter chain
│   ├── JwtTokenProvider
│   ├── JwtAuthenticationFilter
│   ├── AuthService + AuthController (register/login/refresh/logout)
│   ├── @PreAuthorize for RBAC
│   ├── OAuth2 Google login (spring-boot-starter-oauth2-client)
│   ├── EmailService (Gmail SMTP) + forgot-password mã 6 ký tự
│   ├── BCrypt password hashing
│   └── Module 15 coverage: full implementation

Week 2: Content Parser + REST API
├── Day 8-9: HTML Parser (Module 01)
│   ├── Jsoup-based parser for docs/*.html
│   ├── Extract qa-card → Question entity
│   ├── Tags extraction from badges
│   ├── Difficulty inference from content
│   ├── Async parallel parsing (CompletableFuture)
│   └── Module 01 + 02 coverage
│
├── Day 10-11: REST API CRUD
│   ├── QuestionController + endpoints
│   ├── ModuleController, TopicController
│   ├── GlobalExceptionHandler (@ControllerAdvice)
│   ├── Pagination + Sorting
│   ├── Validation (@Valid + custom)
│   ├── SpringDoc OpenAPI (Swagger UI)
│   └── Module 06 coverage: full implementation
│
├── Day 12-14: Migrations + Indexes
│   ├── V001-V005 Flyway migrations
│   ├── Indexes for query optimization
│   ├── EXPLAIN ANALYZE verification
│   ├── Caching with Redis
│   └── Module 05 coverage: schema design
```

### Acceptance Criteria

- [ ] User có thể register/login
- [ ] Login bằng Google OAuth2 hoạt động
- [ ] Quên mật khẩu → nhận email mã 6 ký tự → reset → login lại
- [ ] JWT issued + refresh flow hoạt động
- [ ] Parser đọc được `docs/*.html` → tạo Question records
- [ ] GET /questions?moduleId=X trả về danh sách
- [ ] Admin CRUD question qua REST API
- [ ] Swagger UI tại `/swagger-ui.html`
- [ ] Docker compose chạy được (db + redis + app)
- [ ] Tests pass (unit + integration)

---

## Phase 2: Learning Core (Week 3-4)

### Tasks

```
Week 3: SRS + Flashcard
├── Day 15-16: SRS Domain
│   ├── SRSCard entity
│   ├── SM-2 algorithm implementation (Priority Queue)
│   ├── SRSService
│   ├── Unit tests for SM-2 edge cases
│   └── Module 11 coverage: Priority Queue
│
├── Day 17-18: Flashcard API + Frontend
│   ├── /srs/due endpoint
│   ├── /srs/review/{id} endpoint
│   ├── Frontend FlashcardDeck component
│   ├── Review session UI (flip animation)
│   └── Streak tracking
│
├── Day 19-21: Quiz Mode
│   ├── QuizStrategy interface (Strategy pattern)
│   ├── RandomQuizStrategy
│   ├── WeaknessFocusedStrategy
│   ├── InterviewSimulatorStrategy
│   ├── QuestionOption entity
│   ├── /quiz/generate + /quiz/{id}/submit
│   ├── Auto-generate distractors
│   └── Module 13 coverage: Strategy pattern

Week 4: Progress + Dashboard
├── Day 22-23: Progress tracking
│   ├── StudyAttempt entity
│   ├── UserProgress entity
│   ├── ProgressService (async event listener)
│   ├── Mastery percentage calculation
│   └── Module 02 coverage: event-driven
│
├── Day 24-25: Dashboard data
│   ├── Knowledge Radar (multi-dim data)
│   ├── Heatmap (Date-based aggregation)
│   ├── Leaderboard (Redis sorted set)
│   ├── WebSocket live progress
│   └── Caching strategy (Redis + Caffeine)
│
├── Day 26-28: Code Challenge
│   ├── Challenge entity + test cases
│   ├── Monaco Editor integration
│   ├── Submission endpoint
│   └── Mock Interview mode (text answer)
```

### Acceptance Criteria

- [ ] Flashcard review hoạt động (SM-2 algorithm đúng)
- [ ] Quiz generate + submit + scoring
- [ ] Progress dashboard render radar + heatmap
- [ ] Streak tracking hoạt động
- [ ] Code challenge accept + return test results

---

## Phase 3: Blog Agent (Week 5-6)

### Tasks

```
Week 5: Data Collector
├── Day 29-30: Collector infrastructure
│   ├── CollectorSource + CollectedItem entities
│   ├── CollectorStrategy interface
│   ├── RSSSource (Rome library)
│   ├── GitHubTrendingSource (Jsoup)
│   └── Module 01 coverage: Streams, async
│
├── Day 31-32: Multi-source + Dedup
│   ├── RedditSource (JRAW)
│   ├── Dev.toSource (REST API)
│   ├── YouTubeSource (transcript)
│   ├── Dedup algorithm (hash + similarity)
│   ├── Scoring + categorization
│   └── @Scheduled cron job
│
├── Day 33-35: Topic Selection + AI
│   ├── TopicSelector (4 strategies)
│   ├── Trending, gap, seasonal, demand
│   ├── OpenAI/Anthropic SDK
│   └── Module 02 coverage: CompletableFuture

Week 6: Writer + Publishing
├── Day 36-37: Blog Writer templates
│   ├── BlogTemplate (Template Method pattern)
│   ├── DeepDive, Comparison, Tutorial
│   └── Module 13 coverage: Template Method
│
├── Day 38-39: Quality + SEO
│   ├── Quality scoring
│   ├── SEO optimizer
│   ├── Mermaid diagram generator
│   ├── Cover image (DALL-E)
│   └── Cost tracking
│
├── Day 40-42: Publishing pipeline
│   ├── BlogPost + State pattern
│   ├── Review queue UI (admin)
│   ├── RSS feed generation
│   ├── Social media auto-post
│   ├── Email digest
│   └── Module 07 coverage: Circuit Breaker for AI
```

### Acceptance Criteria

- [ ] Collector chạy mỗi 6 giờ
- [ ] Blog agent viết 1 bài/ngày
- [ ] Score > 85 → auto-publish
- [ ] Admin approve/reject
- [ ] RSS feed accessible

---

## Phase 4: Notes + Polish (Week 7-8)

### Tasks

```
Week 7: Notes + Polish
├── Day 43-44: Notes CRUD
│   ├── Note entity + tags
│   ├── NoteService + NoteController
│   ├── Note → Flashcard conversion
│   └── Public notes sharing
│
├── Day 45-46: Mindmap
│   ├── Knowledge graph builder
│   ├── Cytoscape.js frontend
│   ├── Tag-based clustering, mastery colors
│   └── Module 11 coverage: Graph
│
├── Day 47-49: Export + Polish
│   ├── Export notes (PDF via OpenPDF)
│   ├── Export progress report
│   ├── Daily challenge + notifications
│   ├── Performance tuning (N+1, indexes)
│   └── Module 05 coverage: performance

Week 8: CI/CD + Deploy
├── Day 50-51: CI/CD
│   ├── GitHub Actions CI
│   ├── Docker build + push
│   ├── Staging auto-deploy
│   └── Module 09 coverage
│
├── Day 52-54: Production
│   ├── Deploy to Railway / Render
│   ├── Prometheus + Grafana
│   ├── Backup strategy
│   ├── ADR documentation
│   └── Module 12 coverage: Software Design
│
├── Day 55-56: Launch
│   ├── Launch blog post
│   ├── Seed data for demo
│   ├── Demo video
│   └── Portfolio writeup
```

### Acceptance Criteria

- [ ] Notes CRUD hoạt động
- [ ] Mindmap render đúng
- [ ] Export PDF chất lượng
- [ ] CI/CD pipeline xanh
- [ ] Production deploy ổn định
- [ ] Monitoring dashboard hoạt động
