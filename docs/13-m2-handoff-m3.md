# M2 → M3 Handoff

> Cập nhật: 2026-09-27 · Commit baseline: `766f6f1` + follow-up fix (Flyway/smoke tests enabled)

## M2 đã xong (verification)

| Check | Evidence |
|---|---|
| Flyway V001–V013 → **32 tables** + **1 MVIEW** (`user_topic_mastery`) | `FlywayDatabaseMigrationTest` (Testcontainers, **enabled on CI**) |
| `ddl-auto=validate` + context boot | `ApplicationSmokeTest` — Spring context + Hibernate validate |
| `GET /actuator/health` → 200 | `ApplicationSmokeTest.actuatorHealthReturns200` |
| 32 JPA entities + 32 Spring Data repos | `kg-infrastructure/.../persistence/` |
| Domain port adapter | `UserRepositoryAdapter` ↔ `UserRepository` |
| ArchUnit clean | presentation **không** import `jakarta.persistence` (`PersistenceConfig` ở infra) |
| Canonical OAuth columns | `auth_provider` + `oauth_id` — **không** `oauth_provider` |

## Schema sẵn cho m3 (không tạo lại migration)

Các bảng m3 sẽ **wire use-case**, không thêm Flyway version:

| Table | Migration | Dùng cho |
|---|---|---|
| `users` | V001 | Register / Login / OAuth link |
| `refresh_tokens` | V001 | JWT refresh rotation + reuse detection (Postgres audit layer) |
| `notification_preferences` | V001 | Optional prefs defaults on register |
| `password_reset_codes` | V011 | Forgot-password 6-digit code (SHA-256 hash, TTL 10m) |

Partial unique index sẵn: `idx_users_oauth ON users (auth_provider, oauth_id) WHERE oauth_id IS NOT NULL`.

## Domain đã có (kg-core)

- `User` aggregate + `AuthProvider` (`LOCAL`/`GOOGLE`/`GITHUB`)
- `UserRole` (`USER`/`PREMIUM`/`ADMIN`)
- Ports: `UserRepository`, `PasswordHasher`, `TokenService` — **implement adapters ở m3**

## Config baseline (m3 sẽ mở rộng)

- `application.yml`: datasource, Flyway, Hikari, `ddl-auto=validate`, `open-in-view: false`
- Health: `show-details: when_authorized` (prod: `never`)
- Redis starter **có trên classpath** nhưng **chưa wire** — smoke test exclude Redis autoconfig; m3 bật Redis cho refresh-token cache

## Việc m3 phải làm (không làm lại m2)

1. SecurityFilterChain + JWT access 15m + refresh cookie httpOnly
2. BCrypt `PasswordHasher` adapter (cost 12)
3. Redis refresh-token layer (2-layer với Postgres `refresh_tokens`)
4. Endpoints: register / login / refresh / logout / forgot-password / reset-password
5. Google OAuth2 client (Spring Security) — **không Keycloak**
6. Bucket4j rate limit trên auth endpoints
7. Wire `password_reset_codes` use-case (bảng đã migrate ở V011)

## Test bắt buộc giữ xanh khi cook m3

```bash
./gradlew build   # gồm FlywayDatabaseMigrationTest + ApplicationSmokeTest + ArchUnit
```

Docker daemon cần chạy (Testcontainers `postgres:16-alpine`).

## Known deferrals (không block m3)

- `QaCardFixtureCountTest` — informational; HTML `.qa-card` fixtures đến m4 (content parser)
- V009 indexes placeholder — thêm khi có query pattern thật
- Default Spring Security password warning — thay bằng SecurityConfig ở m3
