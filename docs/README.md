# Knowledge Gym — Docs

> Tổng hợp brainstorm và plan cho nền tảng ôn tập kiến thức IT cá nhân hóa.
> **Nguyên tắc cốt lõi:** mỗi feature trong app = 1 cơ hội thực hành kiến thức trong `docs/` (Java Core, Spring, JPA, DB, REST, Auth, Cloud...).
>
> **Cook source of truth:** `plans/knowledge-gym/plan.md` + 12 mini-phases — **không** dùng `reports/knowledge-gym-plan.md` (STALE).

## Mục lục

| # | File | Nội dung |
|---|------|----------|
| 00 | [overview.md](./00-overview.md) | Vision, scope, kiến trúc tổng quan, tech stack |
| 01 | [ux-modes.md](./01-ux-modes.md) | 10 hình thức ôn tập: Flashcard, Quiz, Mock Interview, Code, Mindmap... |
| 02 | [monetization.md](./02-monetization.md) | Freemium, multi-tenant, content packs, B2B — **deferred Phase 5+** |
| 03 | [notes-system.md](./03-notes-system.md) | User notes, highlight, share, AI summarize |
| 04 | [auto-blog.md](./04-auto-blog.md) | Blog agent, AI writer, templates, scheduling |
| 05 | [data-collector.md](./05-data-collector.md) | Internet sources: RSS, Reddit, GitHub, YouTube |
| 06 | [feature-knowledge-mapping.md](./06-feature-knowledge-mapping.md) | Map từng feature ↔ module docs/ |
| 07 | [erd.md](./07-erd.md) | ERD 32 bảng + tiers MVP/Full + quy tắc |
| 07b | [erd-ddl.sql](./07-erd-ddl.sql) | **Full CREATE TABLE** (cook m02 source) |
| 07c | [erd.mmd](./07-erd.mmd) | Mermaid compact diagram |
| 08 | [rest-api.md](./08-rest-api.md) | ~75 REST endpoints (ADR-002, UPPERCASE enums) |
| 09 | [project-structure.md](./09-project-structure.md) | Gradle Kotlin DSL + Clean Architecture DDD |
| 10 | [phase-roadmap.md](./10-phase-roadmap.md) | ⚠️ **STALE** — dùng 12 mini-phases trong `plans/` |
| 11a | [coverage-matrix.md](./11-coverage-matrix.md) | Module ↔ Feature mapping |
| 11b | [cloud-free-tier.md](./11-cloud-free-tier.md) | PVE + Mailu + Garage + Lambda + PBS + B2 |
| 12 | [redis-strategy.md](./12-redis-strategy.md) | Redis auth/cache/locks/leaderboard/quota |

## Quick start

```bash
# Plan cook (canonical)
cat ../../plans/knowledge-gym/plan.md

# ERD + DDL
cat 07-erd.md
cat 07-erd-ddl.sql

# API
cat 08-rest-api.md
```

## Status

- Schema: **32 bảng + 1 MVIEW**, Flyway V001–V013, tiers **MVP Must 16 / Optional 3 / Full 13**
- Infra: Garage (not MinIO), Mailu (not SES), no Keycloak, no tenants
- Ready to cook: m1 → m2 after DDL review
