# Knowledge Gym — m3 Auth Journal

## 2026-09-27 — m3 Auth Implementation Complete

### Scope
Full authentication module for Knowledge Gym:
- **Register** (`POST /auth/register`) — email/password, BCrypt cost 12, auto-login
- **Login** (`POST /auth/login`) — JWT access 15m + refresh 7d httpOnly cookie
- **Refresh** (`POST /auth/refresh`) — 2-layer strategy (Redis cache O(1) + PostgreSQL audit)
- **Logout** (`POST /auth/logout`) — revoke family + blacklist token
- **Forgot Password** (`POST /auth/forgot-password`) — 6-digit SecureRandom OTP, SHA-256 hash in DB, 10min TTL, max 5 attempts
- **Reset Password** (`POST /auth/reset-password`) — verify code, update password, revoke all families
- **Google OAuth2** — fragment redirect (`#access_token=...`), email_verified required, account linking
- **Bucket4j Rate Limiting** — Redis-backed CAS tokens: login 5/min, register 10/h, forgot-password 3/min, global 100/min/IP

### Architecture Compliance
- **kg-core**: Pure Java domain models, ports, use cases. Zero Spring/JPA imports (enforced by ArchUnit)
- **kg-infrastructure**: Security, persistence, email adapters. `@Configuration` bean wiring via `UseCaseConfig`
- **kg-presentation**: `AuthController`, DTOs (records), RFC 7807 `GlobalExceptionHandler`

### Files Created/Modified
**kg-core** (new):
- `PasswordResetCode.java`, `RefreshToken.java` — domain models
- `TokenService.java` (updated: `verifyRefreshToken` → `RefreshTokenClaims`)
- `RefreshTokenRepository.java`, `PasswordResetCodeRepository.java`, `RefreshTokenCachePort.java`, `EmailService.java` — ports
- `RegisterUseCase.java`, `LoginUseCase.java`, `RefreshTokenUseCase.java`, `LogoutUseCase.java`, `ForgotPasswordUseCase.java`, `ResetPasswordUseCase.java` — use cases
- `HashUtils.java`, `AuthException.java` — utilities
- `RegisterUseCaseTest.java`, `HashUtilsTest.java` — unit tests

**kg-infrastructure** (new):
- `BCryptPasswordHasher.java`, `JwtTokenService.java` — security implementations
- `RedisRefreshTokenCacheAdapter.java` — Redis cache port
- `RefreshTokenRepositoryAdapter.java`, `PasswordResetCodeRepositoryAdapter.java` — JPA adapters
- `GmailEmailService.java` — email service (console fallback in dev)
- `JwtAuthenticationFilter.java`, `RateLimitFilter.java` — servlet filters
- `OAuth2SuccessHandler.java` — Google OAuth2 success handler
- `RateLimitConfig.java` — Bucket4j LettuceBasedProxyManager
- `SecurityConfig.java` — Spring Security filter chain
- `UseCaseConfig.java` — use case bean wiring
- `SpringDataRefreshTokenRepository.java`, `SpringDataPasswordResetCodeRepository.java` — custom queries

**kg-presentation** (new):
- `AuthController.java`, `RegisterRequest.java`, `LoginRequest.java`, `ForgotPasswordRequest.java`, `ResetPasswordRequest.java`, `TokenResponse.java`
- `GlobalExceptionHandler.java` — RFC 7807 error handler
- `AuthIntegrationTest.java`, `TestEmailServiceConfig.java` — integration tests

**Config**:
- `application.yml` — JWT secrets, Redis, OAuth2, Mail settings
- `application-test.yml` — test profile with Testcontainers dynamic properties
- `kg-core/build.gradle.kts` — added assertj for tests
- `kg-infrastructure/build.gradle.kts` — added `spring-boot-starter-web` (servlet API), fixed jjwt `platform()` → direct dependency

### Test Coverage (13 tests, all green)
- `ApplicationSmokeTest`: Spring context boot, Flyway validate, actuator health
- `AuthIntegrationTest`:
  - `register_login_refresh_logout_flow` — full E2E
  - `login_withWrongPassword_returns401`
  - `register_duplicateEmail_returns400`
  - `refresh_reuseAfterRotation_revokesFamily` — reuse detection
  - `rateLimit_login_429After5Attempts`
  - `forgotPassword_returnsGeneric200_andDoesNotEnumerate`
  - `refreshCookie_isHttpOnly_andSecureOffInTestProfile`
  - `forgotPassword_fullFlow_resetsPassword_andRevokesRefreshTokens`
  - `forgotPassword_wrongCode5Times_thenCorrectCode_rejected`

### Gotchas & Fixes
1. **OAuth2 client-id required**: Spring Boot 3.2 rejects empty `client-id` in `OAuth2ClientProperties`. Fix: dummy values in `application-test.yml`.
2. **Rate limit cross-test pollution**: All tests share IP 127.0.0.1, causing 429 across tests. Fix: `X-Forwarded-For` header per test with unique IP.
3. **`platform(jjwt.api)` wrong**: `platform()` is for BOMs; jjwt-api is a regular library. Fix: remove `platform()`.
4. **`RedisCodec` location**: In Lettuce 6.3.2, `RedisCodec` is in `io.lettuce.core.codec`, not `io.lettuce.core`.
5. **Health 503**: Mail health indicator DOWN (no SMTP in test). Fix: `management.health.mail.enabled: false`.
6. **CSP 6.x API**: `ContentSecurityPolicyConfig.requestMatchers()` doesn't exist in Spring Security 6. CSP applies to all responses by default.

### Code Review Follow-up (2026-09-27 evening)

3 blocker defects identified by code-reviewer subagent — all fixed:

1. **Race condition in `RefreshTokenUseCase`** — wrapped in `@Transactional`, reordered Redis-first
   (blacklist OLD trước khi tạo new), added atomic CAS `revokeIfActive(id, replacedBy)`
   trên PG (`UPDATE ... WHERE revoked_at IS NULL`). Concurrent rotation giờ chỉ 1 thread thắng.

2. **6-digit reset code brute-force** — added per-user cooldown (60s) trong `ForgotPasswordUseCase`.
   Chỉ gửi code mới khi code gần nhất đã > 60s; code cũ bị mark-used để không tạo nhiều code active.
   Rate limit 3/min/IP vẫn áp dụng — 2 lớp chống botnet.

3. **OAuth2SuccessHandler no refresh cookie** — added `RefreshTokenCookie` helper (httpOnly Secure
   SameSite=Strict TTL 7d), shared by `AuthController` và `OAuth2SuccessHandler`. OAuth users giờ
   có refresh token đúng chuẩn password login.

### Code Review Follow-up Round 2 (2026-09-27 afternoon)

Additional fixes after second review pass:

1. **LogoutUseCase bug** — trước đây chỉ revoke 1 token trong PG, không revoke family.
   Fix: parse JWT → `revokeFamily` PG + Redis; fallback PG lookup nếu JWT invalid.
2. **Register email leak** — message chứa `"Email already registered: user@x.com"`.
   Fix: generic `"Email already registered"` + `AuthException.Kind.CONFLICT` → HTTP 409.
3. **AuthException.Kind** — UNAUTHORIZED/BAD_REQUEST/CONFLICT map đúng HTTP status
   (reset-password validation → 400 thay vì 401).
4. **Redis blacklist** — `blacklist()` cũng `DELETE rt:{hash}` active key.
5. **JWT clockSkewSeconds(30)** — JwtTokenService + JwtAuthenticationFilter.
6. **CORS** — `app.security.cors.allowed-origins` env-driven (comma-separated).
7. **Prod JWT fail-fast** — `JwtSecretValidator` ném nếu prod dùng default/weak secrets.
8. **X-Forwarded-For** — chỉ tin khi `app.security.trust-forwarded-headers=true`
   (prod=true sau nginx; test=true để cách ly rate-limit buckets).

### Code Review Follow-up Round 3 (deep review MUST-FIX)

From subagent deep re-review — remaining blockers fixed:

1. **OAuth NPE** — `User.createGoogleUser(...)` cho phép `passwordHash=null`; adapter set `email_verified=true`.
2. **Silent LOCAL→Google link** — reject 409 nếu account LOCAL đã tồn tại (chống takeover).
3. **OAuth redirect** — `app.security.oauth2.success-redirect-uri` (absolute http(s) FE URL, validated).
4. **Refresh concurrent session-kill** — PG-first CAS; CAS thua KHÔNG `revokeFamily` (winner giữ session).
5. **Reset code in prod logs** — SMTP fail không log plaintext code; rethrow.
6. **RateLimit config cache** — key theo path only (không per-IP unbounded map).
7. **Register TOCTOU** — `DataIntegrityViolationException` → 409 generic.

### Deferred to m4+
- Email verification flow (need email verification token table + flow)
- RBAC integration tests (need admin endpoint to test 403)
- Google OAuth2 integration test (needs real Google credentials in CI)
- Per-email rate limit on login (currently IP-only due to filter not parsing body)
- H1: JwtAuthenticationFilter doesn't check user exists / not deleted → 15m access TTL acceptable for now
- Access-token denylist on logout (currently only refresh family revoked; access JWT còn sống ≤15m)
- Explicit Google account linking from settings (LOCAL user)