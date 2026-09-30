# Coverage Matrix — Module ↔ Feature ↔ Phase

## Tổng quan

| Module | Features trong app | Phase |
|--------|-------------------|-------|
| **01 Java Core** | Records, String parser, regex, async, exceptions, DTOs | 1 |
| **02 Multithreading** | CompletableFuture parallel parsing, WebSocket, Semaphore, Atomic | 1-2 |
| **03 Spring Boot** | DI, AOP audit, @Scheduled, @Async, profiles, @Cacheable, validators | 1-2 |
| **04 JPA/Hibernate** | @Entity design, @Version, auditing, @EntityGraph, @Specification | 1-2 |
| **05 Database** | Flyway migrations, indexes, MVIEW analytics, EXPLAIN ANALYZE | 1, 4 |
| **06 REST API** | CRUD, pagination, error handling, OpenAPI, versioning | 1 |
| **07 Microservices** | Circuit Breaker, Retry, Rate Limiter cho AI calls | 3 |
| **08 System Design** | L1/L2 cache, multi-tier arch, design docs | 2-4 |
| **09 Cloud/CI/CD** | Docker, Compose, GitHub Actions, Prometheus | 1, 4 |
| **10 Daifuku Domain** | Warehouse-themed UX, bin tracking (optional) | Optional |
| **11 DSA** | SM-2 Priority Queue, Graph mindmap, hash-based dedup | 2, 4 |
| **12 Software Design** | UML diagrams, ADR, SOLID adherence | 4 |
| **13 Design Patterns** | Strategy, Template Method, Observer, Builder, Factory, State | 2-3 |
| **15 Auth/RBAC/OAuth** | JWT, OAuth2, RBAC, Resource Server | 1 |

### Mini-phase đã implement (m4a)

> Các module dưới có phần đã ship thật trong m4a (không chỉ là kế hoạch).

| Module | Kiến thức m4a thực thi | Trạng thái |
|--------|------------------------|------------|
| **05 Database** | Flyway `V014` (unique index `uk_questions_module_sort` natural key) + `V015` (`searchable_text`, tsvector trigger `trg_questions_search`, GIN `idx_questions_search`), native upsert `ON CONFLICT` | Đã ship (m4a) |
| **06 REST API** | `GET /questions` paginated (`page` 1-based, `size` cap 100) + filter + full-text `q`; `GET /questions/{id}`, `/topics`, `/modules`; RFC 7807 `GlobalExceptionHandler` (thêm 409 `ConflictException`, 500 `ContentImportException`); SpringDoc OpenAPI (`OpenApiConfig`), service tại `/api/v1` | Đã ship (m4a) |
| **15 Auth/RBAC** | `@PreAuthorize("hasRole('ADMIN')")` trên `AdminContentController` → USER nhận 403 Problem Details (đóng deferred RBAC của m3) | Đã ship (m4a) |

---

## Feature ↔ Module Detail

| Feature | Module 01 | Module 02 | Module 03 | Module 04 | Module 05 | Module 06 | Module 07 | Module 09 | Module 11 | Module 13 | Module 15 |
|---------|:---------:|:---------:|:---------:|:---------:|:---------:|:---------:|:---------:|:---------:|:---------:|:---------:|:---------:|
| Content Parser | ✅ | ✅ | ✅ | | ✅ | ✅ | | | | | ✅ |
| Auth + JWT | | | ✅ | | | ✅ | | | | | ✅ |
| OAuth2 Google | | | ✅ | | | | | | | | ✅ |
| RBAC | | | ✅ | | | ✅ | | | | | ✅ |
| Question CRUD | | ✅ | ✅ | ✅ | | ✅ | | | | | ✅ |
| Flashcard + SRS | ✅ | ✅ | | ✅ | | | | | ✅ | | |
| Quiz Mode | ✅ | | ✅ | | | ✅ | | | | ✅ | |
| Mock Interview | ✅ | | ✅ | | | ✅ | ✅ | | | | |
| Code Challenge | ✅ | ✅ | | | ✅ | ✅ | | | | | |
| Progress Dashboard | | | ✅ | ✅ | ✅ | ✅ | | | | | |
| Knowledge Mindmap | ✅ | | | | | ✅ | | | ✅ | | |
| Daily Streak | | | ✅ | ✅ | ✅ | | | | | | |
| Blog Agent (Collector) | ✅ | ✅ | ✅ | | ✅ | | ✅ | | | | |
| Blog Agent (Writer) | ✅ | | ✅ | | | ✅ | ✅ | | | ✅ | |
| Notes System | | | ✅ | ✅ | ✅ | ✅ | | | | | |
| Export PDF | ✅ | | | | | | | | | | |
| WebSocket | | ✅ | ✅ | | | | | | | | |
| Admin Dashboard | | | ✅ | | ✅ | ✅ | | | | | ✅ |
| Rate Limiting | | ✅ | ✅ | | ✅ | | | | | | |
| Audit Logging (AOP) | | | ✅ | | | | | | | | |
| Caching (Redis) | | ✅ | ✅ | | | ✅ | | | | | |
| Docker + CI/CD | | | | | | | | ✅ | | | |
| Monitoring | | | | | | | | ✅ | | | |

---

## Coverage %

| Module | Features touching | Coverage |
|--------|-------------------|----------|
| 01 Java Core | Parser, DTO, Records, Collections, Exceptions | **100%** |
| 02 Multithreading | Async parsing, WebSocket, Semaphore, Atomic | **100%** |
| 03 Spring Boot | DI, AOP, Scheduling, Caching, Validation | **100%** |
| 04 JPA/Hibernate | Entity, N+1, Pagination, Auditing, Locking | **100%** |
| 05 Database | Schema, Indexes, Migrations, MVIEW | **100%** |
| 06 REST API | CRUD, Pagination, Error handling, Swagger | **100%** |
| 07 Microservices | Circuit Breaker, Events | **80%** |
| 09 Cloud/CI/CD | Docker, Compose, GitHub Actions, Prometheus | **100%** |
| 11 DSA | Priority Queue, Graph, Fisher-Yates | **90%** |
| 12 Software Design | UML, ADR, SOLID | **80%** |
| 13 Design Patterns | Strategy, Factory, Observer, Builder, Template | **100%** |
| 15 Auth/RBAC/OAuth | JWT, OAuth2, RBAC, Resource Server | **100%** |

**Overall: 14/14 modules covered (100%)**
