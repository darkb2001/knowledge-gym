# Knowledge Gym — Overview

## Vision

Nền tảng ôn tập kiến thức IT cá nhân hóa. Content từ kho notes hiện tại (`docs/`), mở rộng được sang AWS, Kubernetes, Spring... Đăng nhập, theo dõi tiến độ, nhiều hình thức luyện tập.

**Nguyên tắc:** Vừa build vừa học — mỗi feature map về kiến thức trong `docs/`. Không dùng shortcut — dùng đúng annotation, đúng pattern, đúng kiến thức đã học.

## Scope

| Feature | Mô tả |
|---------|-------|
| Đăng nhập | Email + password, **Google OAuth2**, forgot password (mã 6 ký tự qua Mailu self-host SMTP) |
| Multi-user | Mỗi user có tiến độ riêng, streak, notes |
| Multi-topic | Java, AWS, Kubernetes, Spring Advanced... |
| 10 UX Modes | Flashcard, Quiz, Mock Interview, Code, Mindmap, Diagram, Daily Challenge, Radar, Heatmap, Comparison |
| Notes | Ghi chú gắn câu hỏi, bookmark, share, export |
| Auto-Blog | Agent thu thập internet + AI viết blog hàng ngày |

## Tech Stack

| Layer | Tech | Docs module covered |
|-------|------|---------------------|
| Build | **Gradle 8 (Kotlin DSL — `build.gradle.kts`)** | - |
| Arch | **Clean Architecture + DDD** (kg-core / kg-infrastructure / kg-presentation / kg-agent) | 12, 13 |
| Backend | Spring Boot 3.2 + Java 21 | 01, 02, 03 |
| ORM | Spring Data JPA + Hibernate | 04 |
| Database | PostgreSQL 16 + Flyway | 05 |
| Cache + Auth | **Redis 7** (L1/L2 cache + **refresh token 2 lớp** — Redis cache + PostgreSQL audit) | 02, 03 |
| Auth | Spring Security + JWT + OAuth2 (Google, **không Keycloak** — Spring OAuth2 client) + Spring Boot Mail (Gmail SMTP dev / **Mailu self-host prod**) | 15 |
| REST | Spring MVC + OpenAPI | 06 |
| Resilience | Resilience4j (CB, Retry, RateLimiter) | 07 |
| Edge/Gateway | **Nginx** (reverse proxy, load balancer, rate limit tại edge) | 09 |
| Search | **Elasticsearch 8** (global search: questions + notes + blog, hybrid với embedding) | 05, 06 |
| Messaging | **Apache Kafka** (event backbone: blog pipeline publish–subscribe, DLQ) | 07, 14 |
| Agent | @Scheduled + CompletableFuture | 02, 03 |
| Frontend | **Next.js 14** + Tailwind + shadcn/ui (m4b: auth + question browser shipped) | - |
| Diagrams | Cytoscape.js + Mermaid.js | 11 |
| CI/CD | GitHub Actions + Docker + Compose | 09 |
| Monitoring | Prometheus + Grafana (metrics: CPU, RAM, latency, request) | 09 |
| Arch test | ArchUnit (enforce dependency rule) | 12 |
| **Object Storage** | **Garage** (deuxfleurs/garage v2.3.0, self-host Docker, S3-compatible, Rust, AGPLv3, actively maintained) — blog images, avatars, exports | — |
| **DB Backup** | **restic** (encrypted, incremental) → **Backblaze B2** (10 GB free vĩnh viễn) | — |
| **Cloud (free tier, Terraform)** | **AWS Lambda + EventBridge** (cron — free vĩnh viễn), **Firebase Hosting** (frontend). **Bỏ AWS SES** (12-tháng free đã hết → email chuyển sang **Mailu self-host**) | — |

## Architecture

```
                      Users
                       │
                       ▼
┌──────────────────────────────────────┐
│  NGINX (reverse proxy / gateway)     │  ← cổng vào: load balancing,
│  load balance · edge rate limit      │    SSL, cache tĩnh, giảm tải backend
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│  Frontend (Next.js 14)               │
│  ┌──────────┬──────────┬──────────┐   │
│  │ Flashcard│ Quiz/Exam│ Progress │   │
│  │ + SRS    │ + Mock   │ Dashboard│   │
│  └──────────┴──────────┴──────────┘   │
└──────────────┬───────────────────────┘
               │ REST API + WebSocket
┌──────────────▼────────────────────────┐
│  Backend (Spring Boot 3.2)            │
│  ┌────────────┬────────────┬────────┐  │
│  │ Auth (JWT) │ Learning   │Content │  │
│  │ + OAuth2   │ Engine     │Parser  │  │
│  ├────────────┼────────────┼────────┤  │
│  │ Blog Agent │ Notes      │Analytics│ │
│  │ + Collector│ + Export   │+ Radar │  │
│  └─────┬──────┴─────┬──────┴────────┘  │
└────────┼────────────┼─────────────────┘
         │            │
         ▼            ▼
┌──────────────┐ ┌───────────────────────────┐
│ APACHE KAFKA │ │ PostgreSQL 16 + Redis 7   │
│ publish–sub  │ │ Elasticsearch 8 (search)  │
│ blog pipeline│ │ + Caffeine (L1/L2 cache)  │
└──────────────┘ └───────────────────────────┘
         ▲
         │ metrics scrape
┌────────┴───────────────────────────────┐
│  Prometheus → Grafana                   │  ← đo CPU, RAM, latency, request
└────────────────────────────────────────┘
         ▲ cron trigger
┌────────┴───────────────────────────────┐
│  AWS Lambda + EventBridge               │  ← blog collector (6h), daily challenge (6h), backup (3h)
│  (serverless, free vĩnh viễn 1M req/t) │      ⚠ free theo quota — không phải 12-tháng
│  Terraform managed (infra/terraform/)  │
└────────────────────────────────────────┘
         │ trigger API → email
┌────────▼───────────────────────────────┐
│  Mailu self-host (LXC 103 trên PVE)    │  ← production email (forgot-password, notification)
│  Firebase Hosting (10 GB CDN free)     │  ← frontend static hosting
│  Backblaze B2 (10 GB free vĩnh viễn)  │  ← offsite DB backup (restic)
└────────────────────────────────────────┘

(Self-host server local — không cloud DB)
┌────────────────────────────────────────┐
│  PVE node `darkb` (Proxmox VE 9.2.5)  │
│  LXC 102: Docker Compose 9 services     │
│   ├─ Garage (S3-compatible, Rust)     │  ← blog images, avatars, exports (free forever)
│   │  (deuxfleurs/garage:v2.3.0)       │
│   ├─ Nginx + Spring Boot + PG/Redis/  │
│   │   Kafka/ES/Prom/Grafana            │
│   └─ PBS (LXC 104) — snapshot LXC/VM   │  ← infra backup lớp 1 (bootable)
└────────────────────────────────────────┘
```

## Numbers

| Metric | Value |
|--------|-------|
| DB tables | 32 + 1 MVIEW (ADR-002: single-tenant, no tenants table; monetization deferred Phase 5+) |
| API endpoints | ~75 (some sections Deferred) |
| Gradle modules | 4 (kg-core, kg-infrastructure, kg-presentation, kg-agent) — Clean Architecture layers |
| Build script | Kotlin DSL (`build.gradle.kts`) |
| UX modes | 10 (6 MVP + 4 optional) |
| Flyway migrations | V001–**V015** (32 tables + indexes + materialized view; m4a content parser support) |
| Docs modules covered | 15/15 (100%) |
| Infra services (docker-compose, server local) | 9: nginx, app, postgres, redis, kafka, elasticsearch, garage, prometheus, grafana |
| Cloud serverless (free vĩnh viễn, Terraform) | 1: AWS Lambda + EventBridge (+ Firebase Hosting cho FE) — SES đã bỏ (12-tháng free hết) |
| DB backup | restic → Backblaze B2 (10 GB free vĩnh viễn, encrypted, incremental) |
| Duration (MVP) | ~6 tuần |
| Duration (Full) | 10–12 tuần |
| Cloud cost | **$0** — server local (PVE) self-host hết; cloud chỉ Lambda + EventBridge (free vĩnh viễn theo quota), FE Firebase free, email Mailu self-host, object storage Garage self-host, DB backup B2 (10 GB free vĩnh viễn) |
| IaC | **Terraform** (`infra/terraform/`) — AWS Lambda + EventBridge + provisioning LXC trên PVE (`bpg/proxmox`) |
| AI cost | ~$5/tháng (GPT-4o-mini) + per-user quota 50/ngày |
