# Kế hoạch hardening auth/session — đánh giá 4 điểm review

Ngày: 2026-10-11. Trạng thái: **IMPLEMENTED — cả 4 điểm đã triển khai** (xem §10 để đối chiếu
code thật với kế hoạch).

Baseline đọc code: repo `knowledge-gym` + `knowledge-gym-frontend` tại thời điểm viết.

> Tài liệu này giữ nguyên phần phân tích/đánh giá ban đầu (mục 2–9) để làm hồ sơ quyết định.
> Mục 10 ghi lại những gì **thực sự được viết**, khác gì so với dự kiến và bằng chứng test.

Mục đích: phân loại 4 điểm review (absolute expiry, multi-tab race, rollback khi reuse,
CSRF) thành **phải sửa / nên sửa / chưa cần**, kèm bằng chứng code và chỉnh sửa dự kiến.
Đây là tài liệu quyết định, không phải bằng chứng đã kiểm chứng trên production.

## 1. Kết luận phân loại

| # | Điểm review | Đúng với code hiện tại? | Loại | Ưu tiên |
|---|---|---|---|---|
| 3 | `revokeFamily` bị rollback khi phát hiện reuse | **Đúng, nặng hơn mô tả** | Security bug (containment) | **P0 — sửa ngay** |
| 2 | Race đa tab khi rotate token | Đúng một phần (BE đã an toàn) | UX degradation | P1 — nên sửa |
| 1 | Thiếu absolute session expiry | Đúng | Hardening / compliance | P2 — chưa gấp |
| 4 | CSRF khi disable CSRF filter | Đúng lý thuyết, rủi ro thực tế thấp | Defense-in-depth | P2/P3 — tùy chọn |

Không có điểm nào là "review nói sai"; khác biệt nằm ở **mức độ nghiêm trọng thực tế**,
và điểm #3 nghiêm trọng hơn review mô tả.

---

## 2. P0 — #3: revoke family không bền vững trong nhánh reuse

### Bằng chứng

`execute()` được bọc `@Transactional`:

```21:41:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/application/RefreshTokenUseCase.java
public class RefreshTokenUseCase {
    // ...
    @Transactional
    public Result execute(String rawRefreshToken, String ipAddress, String userAgent) {
```

Mọi nhánh reuse đều gọi `revokeFamily` (PG) rồi `throw new AuthException(...)`:

```55:64:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/application/RefreshTokenUseCase.java
        if (cache.isFamilyRevoked(familyId)) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new AuthException("Refresh token family revoked — reuse detected");
        }

        // Blacklisted = đã rotated thành công trước đó → TRUE reuse (stolen/old tab)
        if (cache.isBlacklisted(oldHash)) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new AuthException("Refresh token reuse detected — family revoked");
        }
```

`AuthException` là `RuntimeException`:

```6:13:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/application/AuthException.java
public class AuthException extends RuntimeException {

    public enum Kind { UNAUTHORIZED, BAD_REQUEST, CONFLICT }
```

Adapter không tách transaction:

```48:51:knowledge-gym/kg-infrastructure/src/main/java/com/knowledgegym/infrastructure/persistence/adapter/RefreshTokenRepositoryAdapter.java
    @Override
    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, Instant.now());
    }
```

### Vì sao nghiêm trọng hơn mô tả của review

Review lo "revoke có thể bị rollback". Thực tế có **hai** lỗ hổng chồng nhau trong cùng nhánh:

1. **PG rollback**: `revokeFamily` (UPDATE trong transaction) bị huỷ khi `AuthException`
   propagate ra khỏi `@Transactional`.
2. **Redis không hề được set**: `cache.revokeFamily(familyId, TTL)` **chỉ** được gọi ở nhánh
   rotate thành công, không có trong bất kỳ nhánh reuse nào:

```111:113:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/application/RefreshTokenUseCase.java
        // Redis sau PG thắng — blacklist + store new
        cache.blacklist(oldHash, RefreshToken.TTL);
        cache.store(newHash, userId, familyId, RefreshToken.TTL);
```

Hệ quả cụ thể với kịch bản token bị đánh cắp (token cũ quay lại sau khi đã rotate):

- Kẻ tấn công dùng token cũ → nhận 401 (reuse detection *phát hiện* đúng).
- **Nhưng family không bị thu hồi**: PG rollback, Redis family-revoke không set.
- → Refresh token **mới nhất** của family (đang nằm ở tay kẻ tấn công hoặc nạn nhân)
  **vẫn hoạt động**. Đây đúng là thứ mà rotation + reuse detection sinh ra để chặn.
- Nói cách khác: reuse detection hiện chỉ "từ chối request", chưa "cắt phiên". Nhãn
  "family revoked" trong message lỗi là sai so với hành vi thật.

Thêm một điểm phụ: nhánh thứ ba (dòng 70-73) gọi `cache.store(...)` (Redis, không
transactional) trước khi có thể throw — nghĩa là trạng thái Redis và PG có thể lệch nhau
trong cùng một request.

### Test hiện tại không bắt được

`AuthIntegrationTest.refresh_reuseAfterRotation_revokesFamily` chỉ assert HTTP 401; không
assert family thực sự bị revoke ở PG **và** Redis:

```291:296:knowledge-gym/kg-presentation/src/test/java/com/knowledgegym/presentation/AuthIntegrationTest.java
        // Lần 2: dùng lại token cũ → phải 401 (family revoke)
        mockMvc.perform(post("/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", originalRefresh)))
                .andExpect(status().isUnauthorized());
```

Đây là lý do lỗi tồn tại: test pass vì chỉ kiểm status code.

### Chỉnh sửa dự kiến

Nguyên tắc: **việc thu hồi phải commit độc lập và phải chạm cả Redis**, không nằm chung
transaction sẽ bị rollback.

- Tách một port/thành phần chuyên trách revoke bền vững, chạy `REQUIRES_NEW`:

```java
// kg-core: port mới, ví dụ
public interface RefreshFamilyRevoker {
    /** Thu hồi family một cách bền vững, độc lập với transaction gọi nó. */
    void revokeFamilyDurably(UUID familyId);
}
```

```java
// kg-infrastructure: adapter, PG + Redis trong transaction riêng
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void revokeFamilyDurably(UUID familyId) {
    repository.revokeFamily(familyId, Instant.now());
    cache.revokeFamily(familyId, RefreshToken.TTL); // Redis không transactional nhưng chạy cùng luồng
}
```

- Trong `RefreshTokenUseCase`, thay `refreshTokenRepository.revokeFamily(...)` ở các nhánh
  reuse bằng `revoker.revokeFamilyDurably(familyId)` rồi mới `throw`.
- **Không** đổi `RefreshTokenRepository.revokeFamily` dùng chung thành `REQUIRES_NEW`:
  `LogoutUseCase` và `SessionManagementUseCase` cần revoke atomic chung với
  `invalidateIssuedBefore`:

```94:101:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/application/SessionManagementUseCase.java
    private void revokeFamily(UUID userId, UUID familyId) {
        refreshTokenRepository.revokeFamily(familyId);
        cache.revokeFamily(familyId, RefreshToken.TTL);
        sessionInvalidation.invalidateIssuedBefore(userId, Instant.now());
    }
```

Đổi propagation ở tầng dùng chung sẽ làm mất tính atomic đó.

- Cân nhắc thêm `@Transactional(noRollbackFor = AuthException.class)` **không** giải quyết
  được, vì nó giữ transaction commit cả những ghi khác (ví dụ `save(newAudit)`) — chỉ dùng
  REQUIRES_NEW mới cô lập đúng.

### Acceptance test cho #3

- Integration test (Spring + PG + Redis thật), kịch bản: register → refresh (rotate) →
  dùng lại token cũ → assert:
  - HTTP 401;
  - `SELECT revoked_at FROM refresh_tokens WHERE family_id = ?` **tất cả** row đã revoke;
  - Redis `rt:revoked:{familyId}` tồn tại;
  - refresh token **mới nhất** của family đó → `POST /auth/refresh` trả 401 (containment thật).
- Thêm unit test cho use case: khi mock restart transaction, revoker vẫn được gọi đúng 1 lần
  ở mỗi nhánh reuse (family-revoked, blacklist, already-revoked).

### Việc kéo theo

Mục "Giới hạn cần biết" trong workspace tài liệu đang tự nhận điểm này; sau khi fix phải
cập nhật lại text cho khỏi nói sai:

```112:112:knowledge-gym-frontend/components/admin/ArchitectureWorkspace.tsx
      <ul className="mt-3 list-disc space-y-3 pl-5 text-sm leading-7 text-body"><li>{c("refreshInFlight là biến module: gộp request trong cùng tab, không có cross-tab lock. Multi-tab vẫn có thể reuse token cũ; không gọi mọi 401 là token bị đánh cắp.", "refreshInFlight is module state: coalesces within one tab, with no cross-tab lock. Multiple tabs can still reuse an old token; not every 401 means theft.")}</li><li>{c("Nhánh reuse trong RefreshTokenUseCase gọi revokeFamily rồi ném AuthException trong @Transactional. Cần kiểm chứng rollback/commit ở integration test trước khi hứa family revoke bền vững; diagram biểu diễn ý định branch, không chứng nhận hiệu lực production.", "Reuse branches call revokeFamily and then throw AuthException inside @Transactional. Verify rollback/commit in integration tests before promising durable family revocation; diagrams describe branch intent, not production certification.")}</li>
```

---

## 3. P1 — #2: race đa tab khi rotate token

### Bằng chứng

`refreshInFlight` là biến module — chỉ coalesce trong **một tab**:

```19:19:knowledge-gym-frontend/lib/api-client.ts
let refreshInFlight: Promise<boolean> | null = null;
```

Tab thua nhận 401 và bị coi là "session chết":

```190:196:knowledge-gym-frontend/lib/api-client.ts
      // Definitive auth failure only — cookie gone / revoked / expired.
      if (res.status === 401 || res.status === 403) {
        clearSession();
        return false;
      }
```

```274:281:knowledge-gym-frontend/lib/api-client.ts
    try {
      const refreshed = await tryRefresh();
      if (refreshed) {
        return apiRequest<T>(path, { ...options, _retried: true });
      }
      // Refresh cookie rejected — session is dead.
      bounceToLogin();
```

### Vì sao impact thấp hơn mô tả của review

1. **Backend đã chống race.** CAS `revokeIfActive` đảm bảo chỉ một request thắng, và kẻ thua
   **không** bị revoke family:

```103:108:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/application/RefreshTokenUseCase.java
            boolean revoked = refreshTokenRepository.revokeIfActive(oldOpt.get().getId(), savedNew.getId());
            if (!revoked) {
                // Concurrent loser — KHÔNG revokeFamily (winner vẫn valid)
                throw new AuthException("Refresh token already rotated — retry with latest cookie");
            }
```

2. **Refresh cookie dùng chung theo browser** (host API, path `/`), nên `Set-Cookie` của tab
   thắng thường đã cập nhật token cho mọi tab trước khi tab khác kịp gửi.

Hệ quả thực tế: **không mất dữ liệu**, chỉ nháy giật UX — tab thua bị đá về `/login`, và
`/login` lại gọi `ensureAccessToken()` bằng cookie mới nên vào lại app ngay. Trừ khi điểm #3
chưa được fix, **cộng hưởng** xảy ra: nếu tab thua đi vào nhánh blacklist và (sau khi fix #3)
family bị revoke thật, thì race đa tab có thể **giết session hợp lệ của chính người dùng**.

→ Đây là lý do #2 nên sửa **sau** #3: fix #3 mà không fix #2 sẽ biến "401 generic" thành
"logout thật".

### Chỉnh sửa dự kiến (chọn 1)

- **Ưu tiên A — Web Locks API** (đơn giản, chuẩn, không cần đổi backend): bọc `tryRefresh`
  trong `navigator.locks.request("kg-refresh", ...)` để các tab serialize; tab chờ thấy token
  mới qua cookie thì không cần gọi `/refresh` nữa. Cần fallback khi `navigator.locks` không
  tồn tại (giữ hành vi hiện tại).
- **Ưu tiên B — BroadcastChannel**: tab thắng broadcast "token mới / đã rotate", tab khác
  ngừng gọi refresh.
- **Ưu tiên C — grace period ở backend**: cho phép token vừa bị thay thế vẫn chấp nhận trong
  10–30 giây (phân biệt "vừa rotate" với "reuse thật"). Đây là cách phổ biến nhưng cần cẩn
  thận để **không làm mềm** chính tín hiệu reuse detection — chỉ nên coi là "in-flight, trả
  lại token mới nhất" chứ không cấp thêm token mới.

Khuyến nghị: **A trước, C chỉ khi còn tái hiện**. Không nên làm C + A cùng lúc vì sẽ khó
phân biệt regression của #3.

### Acceptance test cho #2

- Browser test nhiều tab: login ở tab A, mở 3 tab, ép access token hết hạn, trigger request
  đồng thời ở cả 3 tab → chỉ **một** `POST /auth/refresh` (hoặc tối đa một lần thành công),
  không tab nào bị đá về `/login`, và không có family revoke trong PG/Redis.
- FE unit test: `navigator.locks` mock, assert hai lời gọi `ensureAccessToken()` đồng thời
  chỉ sinh một request.

---

## 4. P2 — #1: absolute session expiry

### Bằng chứng

TTL reset mỗi lần rotate; không có mốc neo theo family/login gốc:

```12:12:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/domain/model/RefreshToken.java
    public static final Duration TTL = Duration.ofDays(7);
```

```28:31:knowledge-gym/kg-core/src/main/java/com/knowledgegym/identity/domain/model/RefreshToken.java
    public RefreshToken() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plus(TTL);
    }
```

Cookie cũng sliding cùng TTL:

```18:18:knowledge-gym/kg-infrastructure/src/main/java/com/knowledgegym/infrastructure/security/RefreshTokenCookie.java
    public static final int MAX_AGE_SECONDS = 7 * 24 * 3600;
```

Và bản thân tài liệu kiến trúc đã tự thừa nhận "không có absolute family expiry".

### Vì sao chưa gấp

- Reuse detection + blacklist + revoke family (khi #3 được fix) đã là lớp containment cho
  token bị đánh cắp. Rủi ro chính của rolling vô hạn — token sống mãi — phần lớn đã giảm.
- Absolute expiry thuần là **hardening/compliance** (OWASP khuyến nghị), không phải lỗ hổng
  khai thác trực tiếp với MVP học tập.

### Chỉnh sửa dự kiến (khi làm)

Ưu tiên cách không cần migration: mang `familyIssuedAt` như claim trong refresh JWT và giữ
nguyên khi rotate, chặn khi `now - familyIssuedAt > 30 ngày`:

- `TokenService.generateRefreshToken(userId, familyId)` → thêm tham số mốc family (hoặc tái
  dùng claim có sẵn + `fid`); `verifyRefreshToken` trả thêm `familyIssuedAt`.
- Trong `RefreshTokenUseCase`, sau verify: nếu quá `app.security.refresh.absolute-ttl`
  → revoke family bền vững + trả 401 buộc login lại.
- Fallback nếu cần cột: `refresh_tokens.family_created_at` (migration mới, backfill =
  `MIN(created_at)` theo family). Chỉ chọn cách này nếu JWT claim không đủ (ví dụ cần admin
  truy vấn).

### Acceptance test cho #1

- Unit test với `Clock` cố định: family tạo ngày 0; ở ngày 29 rotate OK; ở ngày 31 → 401 và
  family bị revoke; cookie bị clear.

---

## 5. P2/P3 — #4: CSRF khi disable filter

### Bằng chứng

```61:63:knowledge-gym/kg-infrastructure/src/main/java/com/knowledgegym/infrastructure/config/SecurityConfig.java
            .cors(cors -> cors.configurationSource(corsConfig()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

Không tìm thấy kiểm tra `Origin`/`Referer` cho mutating request ở backend (grep toàn repo
infrastructure: chỉ có `SameSite` trong cookie helper, không có `getHeader("Origin")`).

### Vì sao rủi ro thực tế thấp

- Mutation nghiệp vụ (notes, quiz, profile…) xác thực bằng **Bearer access token trong
  memory** (`Authorization` header), không cookie → cross-site attacker không tạo được
  request có credential.
- Chỉ `/auth/refresh`, `/auth/logout` dựa cookie; cookie là `HttpOnly` + `Secure` +
  host-only + `SameSite=Strict`.
- Lo "domain con / redirect phức tạp": `io.vn` là public suffix (đã kiểm PSL), nên registrable
  domain là `darkb-tech.io.vn` → `app.darkb-tech.io.vn` và `api.darkb-tech.io.vn` là
  **same-site**, `Strict` vẫn gửi cookie cho XHR same-site từ FE. Thiết kế hiện tại đúng.

### Khi nào mới cần làm

Chỉ cần khi: (a) phải nới `SameSite` (ví dụ thêm subdomain khác registrable domain), hoặc
(b) thêm endpoint mutation dựa hoàn toàn vào cookie. Khi đó thêm filter kiểm tra
`Origin`/`Referer` allowlist cho các method mutating trên path `/auth/*`.

### Nếu làm (defense-in-depth)

- `OncePerRequestFilter` chạy trước auth filter: với `POST/PUT/PATCH/DELETE`, nếu request
  **có** cookie `refreshToken` thì bắt buộc `Origin` (hoặc `Referer`) thuộc
  `app.security.cors.allowed-origins`; thiếu/sai → 403.
- Không áp cho `/actuator/**` và `/internal/**` (token riêng, không cookie browser).
- Test: POST `/auth/refresh` với `Origin: https://evil.example` → 403; với origin hợp lệ → 200.

---

## 6. Thứ tự thực hiện đề xuất

1. **#3 (P0)**: `RefreshFamilyRevoker` REQUIRES_NEW + Redis set trong nhánh reuse; integration
   test khẳng định family bị revoke thật ở cả PG và Redis; cập nhật text docs workspace.
2. **#2 (P1)**: Web Locks trong `tryRefresh` + browser multi-tab test. Làm sau #3 để tránh
   nhầm lẫn giữa "UX race" và "revoke thật".
3. **#1 (P2)**: absolute expiry 30 ngày/family (claim-based, không migration nếu được).
4. **#4 (P3)**: Origin/Referer guard cho endpoint cookie-auth — chỉ khi có lý do cụ thể.

## 7. Không nên làm

- Không đổi `RefreshTokenRepository.revokeFamily` (dùng chung) sang `REQUIRES_NEW`.
- Không thêm grace period ở #2 trước khi fix #3 — sẽ làm mờ tín hiệu reuse detection.
- Không bật/thêm gì cho #1 và #4 khi chưa có gate `26-beta-release-gates.md` liên quan.

## 8. Verification plan (chung cho cả 4)

- Backend: `bash scripts/test-admin-platform.sh --local-postgres` (core + infrastructure +
  MVC, zero failures), cộng integration test mới cần Docker — phải chạy full CI trên runner
  có Docker, không dựa vào unit test thuần.
- Frontend: `npm run typecheck && npm run lint && npm test && npm run build`.
- Browser: multi-tab refresh scenario (điểm #2) và reuse containment (điểm #3) trên
  full-stack disposable, không dùng production.
- Không thực hiện mutation/deploy trên production trong đợt này.

## 9. File dự kiến chạm

| Path | Hành động |
|---|---|
| `kg-core/.../identity/application/RefreshTokenUseCase.java` | Modify (gọi revoker, absolute expiry ở #1) |
| `kg-core/.../identity/domain/port/RefreshFamilyRevoker.java` | Create (port mới) |
| `kg-infrastructure/.../security/` hoặc `persistence/adapter/` | Create adapter REQUIRES_NEW |
| `kg-core/.../identity/domain/port/TokenService.java` + `JwtTokenService.java` | Modify (#1, claim family anchor) |
| `knowledge-gym-frontend/lib/api-client.ts` | Modify (Web Locks — #2) |
| `kg-presentation/src/test/.../AuthIntegrationTest.java` | Extend (assert revoke thật) |
| `knowledge-gym-frontend/components/admin/ArchitectureWorkspace.tsx` | Modify (cập nhật "Giới hạn cần biết" sau #3) |
| `kg-infrastructure/.../config/SecurityConfig.java` | Modify chỉ khi làm #4 |

---

## 10. Trạng thái triển khai (2026-10-11)

### 10.1 #3 P0 — durable revoke (đã xong)

Khác kế hoạch ở **tên method**: kế hoạch viết `revokeFamilyDurably`, code dùng
`revokeDurably` cho gọn/khỏi trùng `revokeFamily` của repository.

| File | Thay đổi |
|---|---|
| `kg-core/.../identity/domain/port/RefreshFamilyRevoker.java` | **Mới** — port thuần Java, không Spring (ArchUnit `domain_port_khong_import_spring` vẫn xanh). |
| `kg-infrastructure/.../persistence/adapter/DurableRefreshFamilyRevoker.java` | **Mới** — `@Transactional(REQUIRES_NEW)` + `repository.revokeFamily(familyId, now)` + `cache.revokeFamily(familyId, TTL)`. |
| `kg-core/.../identity/application/RefreshTokenUseCase.java` | Mọi nhánh reuse đều đi qua `revokeAndThrow(...)` → `familyRevoker.revokeDurably(...)` rồi `throw`. |

Hai điều chỉnh so với kế hoạch, vì code thật khác giả định:

- **Không dùng `@Transactional(noRollbackFor = AuthException.class)`** — kế hoạch đã loại,
  code cũng loại; lý do giữ nguyên: nó commit luôn cả `save(newAudit)` của request đang lỗi.
- **`revokeAndThrow` nuốt lỗi hạ tầng (`try/catch` + `log.error`)**. Kế hoạch không nói tới
  trường hợp Redis/DB blip lúc revoke. Chọn giữ hành vi 401 cũ thay vì để lộ lỗi 500 cho
  request vốn phải bị từ chối, nhưng log ERROR để phát hiện family chưa bị cắt.

Bằng chứng test:

- `RefreshTokenUseCaseTest` (mới, không cần Docker): 8 case, khoá hợp đồng "revoker được gọi,
  `repository.revokeFamily` KHÔNG được gọi" ở cả 4 nhánh (family-revoked, blacklist,
  already-revoked, quá absolute TTL), cộng 3 case hành vi rotation.
- `AuthIntegrationTest.refresh_reuseAfterRotation_revokesFamily` (Docker) nay assert thêm
  `count(*) FROM refresh_tokens WHERE family_id=? AND revoked_at IS NULL = 0` và token **mới
  nhất** của family cũng phải 401.

### 10.2 #2 P1 — cross-tab serialize (đã xong)

- `lib/api-client.ts`: `tryRefresh` gọi qua `withRefreshLock(...)`; `navigator.locks.request
  ("kg-auth-refresh", ...)` khi có, fallback chạy trực tiếp khi không.
- **Quan trọng hơn kế hoạch**: bên trong lock so `accessToken` với ảnh chụp trước khi chờ. Tab
  vào lock sau thấy token đã bị tab trước đổi → trả `true` luôn, không gửi `/auth/refresh`
  thứ hai. Đây chính là điều kế hoạch mô tả ("tab chờ thấy token mới") nhưng cần snapshot cụ
  thể mới làm được.
- Đã **loại** một phương án sai lúc code: early-return `if (hasUsableAccessToken())` trong lock.
  Nó phá luồng retry-sau-401 (token còn hạn theo đồng hồ máy nhưng backend đã từ chối) và làm
  2 test cũ đỏ. Ghi lại để không ai thêm lại.
- Test: `api-client.test.ts` +2 case (dùng lock, serialize waiter; fallback khi thiếu API).

### 10.3 #1 P2 — absolute family lifetime (đã xong)

- Claim JWT tên `fiatMs` (`CLAIM_FAMILY_ISSUED_AT_MILLIS`) trong `JwtTokenService`, đúng như
  kế hoạch "claim-based, không migration".
- `TokenService` thêm overload `generateRefreshToken(userId, familyId, familyIssuedAt)`; overload
  2 tham số neo mốc = now và dùng ở login/register/OAuth (family mới).
  `RefreshTokenClaims` thêm field `familyIssuedAt` (**nullable**).
- `RefreshTokenUseCase` rotate xong **giữ nguyên** `claims.familyIssuedAt()` khi phát token mới.
  Nếu reset về now thì absolute expiry thành no-op — có test `rotationPreservesOriginalFamilyAnchor…`
  bắt đúng điều này.
- Policy 30 ngày nằm ở `AuthController` (`app.security.refresh.absolute-family-ttl`, mặc định
  `30d`), truyền xuống use case qua tham số `Duration`; `execute(...)` 3 tham số giữ lại và
  `@Deprecated`, truyền `null` = tắt.
  `execute(..., Duration)` còn coi duration `null`/`0`/âm là **tắt hẳn** policy — cần cho đường
  rollback nhanh trên prod không phải build lại, và có test riêng.
- **Quyết định không có trong kế hoạch**: token phát TRƯỚC khi có claim → `familyIssuedAt == null`
  → bỏ qua check. Không suy mốc từ `iat` (iat là mốc rotate hiện tại, làm vậy biến check thành
  no-op) và không bịa `now` (đá oan phiên đang sống). Có test
  `legacyRefreshTokenWithoutFamilyLifetimeClaim_stillRotates`.
- Test: `RefreshTokenUseCaseTest` (quá hạn → revoker gọi, không phát token), `AuthIntegrationTest`
  (`refreshAfterAbsoluteFamilyLifetime_isRejectedAndRevokesFamily` phát token với mốc neo 31 ngày
  trước → 401 + family revoke).

### 10.4 #4 P3 — Origin/Referer guard (đã xong)

- `OriginGuardFilter` (mới, `@Component`, `OncePerRequestFilter`): mutating + **có cookie
  `refreshToken`** → bắt buộc `Origin` (ưu tiên) hoặc `Referer` thuộc
  `app.security.cors.allowed-origins`; nếu không → 403 JSON.
- Gắn trong `SecurityConfig` bằng `.addFilterAfter(originGuardFilter, CorsFilter.class)`.
- Khác kế hoạch: **allowlist rỗng ⇒ filter tự tắt** thay vì chặn hết. Kế hoạch chỉ nói "chỉ khi
  có lý do cụ thể"; nếu để chặn-hết thì môi trường chưa set `CORS_ALLOWED_ORIGINS` sẽ chết toàn
  bộ `/auth/refresh|logout`, một regression tệ hơn lỗ hổng đang phòng. Có test cho nhánh này.
- Cờ tắt: `app.security.csrf.origin-guard` (mặc định true), có test.
- **Bypass tự phát hiện khi review lại và đã vá**: so khớp Referer ban đầu dùng `startsWith`
  trần, nên allowlist `https://app.example.vn` khớp nhầm `https://app.example.vn.evil.example/...`
  (tên miền khác, cùng tiền tố chuỗi) → một bypass cross-site thật. Nay Referer phải **bằng**
  origin allowlist hoặc có origin đó làm prefix **kết thúc bằng `/`** (ranh giới host). Origin
  không dính lỗi này vì được so bằng `equals` sau normalize. Có 2 test hồi quy cho tiền tố.
- Test: `OriginGuardFilterTest` (mới, MockHttpServletRequest, **12 case**: origin hợp lệ/lạ, thiếu
  cả hai, Referer hợp lệ/lạ, tiền tố Referer/Origin giả mạo, không cookie, GET, internal/actuator,
  cờ off, allowlist rỗng).
- `AuthIntegrationTest`: mọi request mang cookie nay kèm `Origin` hợp lệ; 3 case mới cho guard.

### 10.5 Tài liệu & UI

- `ArchitectureWorkspace.tsx`: 3 bullet "Giới hạn cần biết" viết lại (cross-tab, durable revoke,
  absolute expiry); note CSRF thêm `OriginGuardFilter`; intro "Vòng đời phiên" nói hạn 30 ngày;
  thêm mục "phải login lại" khi quá hạn cứng; `LifetimeExample` giải thích timeline bị chặn.
- `lib/architecture.ts`: `AUTH_SNAPSHOT.absoluteSessionDays = 30`; `rollingSessionExample` cap
  `expiresDay` bằng hạn tuyệt đối; mô tả diagram `refresh` + `storage.Refresh JWT` cập nhật.
- `architecture.test.ts`: cập nhật assertion + 1 case mới "caps a continuously refreshed session".

### 10.6 Verification đã chạy

| Lệnh | Kết quả |
|---|---|
| `./gradlew compileJava compileTestJava` | pass (JDK 21 qua `JAVA_HOME=/opt/homebrew/opt/openjdk@21`) |
| `./gradlew :kg-core:test --rerun-tasks` | pass — **294** test, 0 failure |
| `:kg-infrastructure:test --tests OriginGuardFilterTest --tests JwtAuthenticationFilterTest --tests ArchTest` | pass — **14** test, 0 failure |
| `npm run test` | 39 file / **718** test pass |
| `npm run typecheck` | pass |
| `npm run lint` | pass |
| `npm run build` (`next build`) | pass |

### 10.7 Còn lại / chưa kiểm chứng

- **CI (có Docker) đã chạy nhóm `@Testcontainers`** và bắt được 1 lỗi trong helper của
  `AuthIntegrationTest`: `readJson` chỉ thay dấu `.` đầu tiên nên `$.user.id` → pointer sai
  (`Invalid UUID string:`). Đã sửa (đổi hết dấu chấm). Máy dev không có Docker nên lỗi này chỉ
  lộ ra trên CI — đúng lý do phải chạy CI trước khi coi là kiểm chứng.
- Multi-tab browser E2E (acceptance #2) mới có unit test, chưa chạy browser thật.
