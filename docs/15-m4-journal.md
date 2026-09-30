# Knowledge Gym — m4 (m4a+m4b) Content Parser + REST API Journal

## 2026-09-30 — m4 Complete (m4a Backend + m4b Frontend)

### Scope
**m4a** (backend, ✅ done): import `docs/` vào DB và phục vụ qua REST + Swagger.  
**m4b** (frontend, ✅ done): Next.js 14 App Router + TypeScript + Tailwind — auth flows + question browser.

- **Content parser** — `JsoupContentSource` đọc `index.html` (topics/modules) + `NN-slug.html`
  (questions), parse song song bằng `CompletableFuture`.
- **Idempotent import** — natural key `(module_id, sort_order)` + native upsert, chạy `/parse`
  nhiều lần không nhân đôi dữ liệu.
- **Full-text search** — `V015` thêm `searchable_text` + trigger tsvector + GIN index.
- **Public REST** — `GET /questions` (filter + search + phân trang), `/questions/{id}`, `/topics`,
  `/modules`.
- **Admin REST** — `POST /admin/content/parse` (202 + async job), CRUD `/admin/content/questions`,
  `@PreAuthorize("hasRole('ADMIN')")` → đóng deferred RBAC từ m3.
- **Cache** — Caffeine L1, evict toàn bộ khi mutation hoặc import xong.
- **Swagger UI** — chạy thật ở dev (permitAll + CSP `script-src`).

### Architecture Compliance
- `kg-core` vẫn **Java thuần**: không có Spring/JPA trong `content/**` (ArchUnit enforce).
- `@EnableCaching` ở `kg-presentation` (nơi `@Cacheable` được dùng), `CacheConfig` ở
  `kg-infrastructure` (cần dependency Caffeine) — proxy không cắt ngang ranh giới module.
- Use case không tự check quyền; `@PreAuthorize` nằm ở controller.
- Presentation không import `infrastructure.persistence` (ArchUnit `PresentationLayerArchTest`).

### Files Created (chính)

**kg-core** — `com.knowledgegym.content`
- `domain/model/`: `ContentCatalog`, `ParsedQuestion`, `Question`, `QuestionOption`, `QuestionQuery`,
  `ModuleRef`, `Topic`, `ModuleWithStats`, `TopicWithStats`
- `domain/port/`: `ContentSource`, `QuestionRepository`, `ModuleRepository`, `TopicRepository`,
  `AnswerHtmlSanitizer`
- `application/`: `ImportContentUseCase`, `QueryQuestionsUseCase`, `GetQuestionDetailUseCase`,
  `ManageQuestionsUseCase`, `CatalogQueryUseCase`, `SearchText`, `ModuleDifficultyDefaults`,
  `ContentImportException`

**kg-core** — `com.knowledgegym.shared`
- `application/NotFoundException` (→404), `application/ConflictException` (→409)
- `domain/model/PageResult` (paging không phụ thuộc Spring Data `Page`)

**kg-infrastructure**
- `content/JsoupContentSource`, `content/JsoupAnswerHtmlSanitizer`, `content/ContentImportJobService`
- `persistence/adapter/`: `QuestionRepositoryAdapter`, `TopicRepositoryAdapter`, `ModuleRepositoryAdapter`
- `persistence/dao/QuestionSearchDao` (native tsquery + `ts_rank`)
- `config/CacheConfig` (Caffeine), `config/AppContentProperties` (`app.content.*`)
- `db/migration/V014__content_import_support.sql`, `V015__questions_fulltext_search.sql`

**kg-presentation**
- `rest/content/`: `QuestionController`, `QuestionDetailController`, `TopicController`,
  `ModuleController`, `AdminContentController`
- `rest/content/dto/`: `QuestionSummaryDTO`, `QuestionDetailDTO`, `QuestionOptionDTO`,
  `AdminQuestionDTO`, `TopicDTO`, `ModuleDTO`, `PageResponse`
- `config/CachingConfig`, `config/OpenApiConfig`

### Files Modified
- `advice/GlobalExceptionHandler` — thêm `ConflictException`→409
- `persistence/repository/SpringDataQuestionRepository` — native `upsert` + `countGroupedByModule`
- `config/SecurityConfig` — permitAll cho `/v3/api-docs/**`, `/swagger-ui/**` + CSP
- `security/RateLimitFilter` — dùng `getServletPath()` (context-path `/api/v1` làm `getRequestURI()`
  không còn khớp pattern → rate limit im lặng bỏ qua)
- `security/RefreshTokenCookie` — `app.security.cookie.same-site`
- `application.yml` — `context-path: /api/v1`, `app.content.docs-path`, caffeine TTL, swagger flag
- `infra/nginx.conf`, `infra/prometheus.yml` — thêm prefix `/api/v1`

### Quyết định quan trọng khi implement

1. **`docs/` nằm ngoài git root** (`knowledge-gym/`), nên không có relative path nào đúng cho mọi
   ngữ cảnh (working dir lúc `bootRun` là `kg-presentation/`, trong container là `/app`).
   `AppContentProperties.Content.resolveDocsPath()` thử lần lượt giá trị cấu hình rồi các ứng viên
   tương đối, và chỉ nhận ứng viên **thật sự là thư mục docs** (có `index.html` + `NN-slug.html`) —
   `knowledge-gym/docs/` cũng là thư mục nhưng chỉ chứa markdown kế hoạch.

2. **`SearchText` — cầu nối tiếng Việt ↔ tiếng Anh.** Câu trong `docs/` viết bằng thuật ngữ Anh,
   người học gõ tiếng Việt không dấu. Bốn cơ chế bù: bỏ dấu, n-gram gạch nối (`bất đồng bộ` →
   `bat-dong-bo`), tách slug, và bảng song ngữ (`sao-luu`⟷`backup`/`replication`,
   `bo-nho`⟷`memory`/`heap`). Postgres dùng config `simple` (không stemming) vì nội dung trộn 2 ngôn
   ngữ — stemming tiếng Anh sẽ băm nát từ tiếng Việt.

3. **Thứ tự token trong `SearchText.build` là invariant, không phải chi tiết vặt.** Title/n-gram/
   synonym vào trước, answer vào cuối, vì answer rất dài (median ~146 token/câu) và sẽ nuốt hết
   budget 140 token nếu chạy trước. Nhưng synonym cũng có thể được kích hoạt **chỉ từ answer**
   (`garbage` trong câu trả lời → `thu-hoi-rac`), nên answer được gom vào set riêng để làm trigger,
   rồi mới append. Hai yêu cầu này xung đột nếu chỉ collect answer một lần — xem mục Regression.

4. **Admin CRUD không dùng chung upsert với import.** Upsert theo natural key là đúng cho re-import
   (idempotent) nhưng sai cho admin: gửi `sortOrder` trùng sẽ ghi đè câu của người khác và vẫn trả
   201. Admin đi qua `QuestionRepository.save` sau khi pre-check `findByModuleIdAndSortOrder`
   → `ConflictException` → 409. Unique index `uk_questions_module_sort` là backstop dưới race.

5. **Sanitize là invariant của domain, không phải của parser.** `AnswerHtmlSanitizer` là port ở
   `kg-core` vì **cả hai** đường ghi phải đi qua: import (`JsoupContentSource`) và admin
   (`ManageQuestionsUseCase`). Nếu chỉ sanitize ở import, admin lưu `<script>` rồi
   `GET /questions/{id}` trả nguyên văn — stored XSS.

6. **Cache content có 3 tên, không phải 1.** `topics`/`modules` chứa `moduleCount`/`questionCount`
   phái sinh, nên thêm/xoá 1 câu hỏi làm số đếm trong 2 cache kia sai ngay. Mọi mutation evict cả 3.

### Test Coverage (m4a)

| Test | Số case | Nội dung |
|---|---|---|
| `JsoupContentSourceTest` | 13 | parse topics/modules/questions đúng số, card thiếu badge/heading, sortOrder tuần tự, strip speak-notes/flow-diagram, sanitize XSS (script/iframe/onclick/javascript:), giữ class FE cần, giữ link tuyệt đối + bỏ link tương đối, tags từ section, difficulty theo module, searchKeywords có synonym |
| `SearchTextTest` | 12 | bỏ dấu, n-gram, tách slug, song ngữ 2 chiều, stopword (không loại `sao`/`cau`/`phan`), budget 140, dedupe, synonym chỉ trigger từ answer |
| `ManageQuestionsUseCaseTest` | 12 | sanitize khi create/update, recompute searchKeywords khi title/answer/tags đổi (và **không** recompute khi chỉ đổi difficulty), 409 khi sortOrder trùng, 404 khi module/question không tồn tại, từ chối answer rỗng sau sanitize |
| `ContentApiIntegrationTest` | 16 | E2E thật (Postgres + Redis Testcontainers): import đúng 3 topic/4 module/13 câu, re-import idempotent, phân trang + filter, full-text search (kể cả gõ không dấu), DTO không lộ `isCorrect`, 404/403 Problem Details, cache evict sau mutation, sanitize qua HTTP, context-path `/api/v1`, Swagger UI 200 |
| `FlywayDatabaseMigrationTest` | — | đủ 15 migration, `uk_questions_module_sort`, `idx_questions_search` |

`./gradlew clean build` xanh toàn bộ (kg-core, kg-infrastructure, kg-presentation) trên JDK 21.

### Gotchas & Fixes

1. **`on*/` trong Javadoc làm vỡ build.** Comment `(bỏ script/iframe/on*/javascript:)` chứa `*/`
   → đóng block comment sớm, javac báo `<identifier> expected`. Viết lại bằng `{@code ...}`.

2. **`ManageQuestionsUseCase` return type của bean phải là `TaskExecutor`.** Khai `Executor` thì
   Spring suy bean type từ return type và không autowire được vào chỗ cần `TaskExecutor`.

3. **`@CacheEvict` không hoạt động ở thân task async.** `ContentImportJobService.run()` chạy qua
   `taskExecutor.execute(() -> run(jobId))` — self-invocation nên proxy không cắt ngang, annotation
   sẽ **im lặng** không làm gì. Phải gọi `cacheManager.getCache(name).clear()` thủ công.

4. **Mojibake trong test helper.** `MockHttpServletResponse.getContentAsString()` không tham số
   dùng `characterEncoding` của response (mặc định ISO-8859-1), biến mọi tiếng Việt thành mojibake.
   Test so khớp chuỗi tiếng Việt sai một cách khó thấy. Fix: đọc UTF-8 tường minh.

5. **Gradle `--tests` filter không match gì thì fail task.** Dùng nó như tín hiệu xác nhận test có
   thật sự chạy, tránh trường hợp "build xanh" nhưng test bị skip.

### Code Review Follow-up (2026-09-30)

Reviewer báo **không có Blocker**; 8/8 acceptance criteria của m4a đạt. Các finding đã xử lý:

1. **`ContentImportJobService` không evict cache khi import thất bại** (Should-fix, defect thật của
   chính change này). `ImportContentUseCase.execute` ghi topics → modules → questions **không** bọc
   transaction, nên exception giữa chừng vẫn để lại các upsert đã commit. `evictContentCaches()`
   phải nằm trong `finally` — nếu không, `questionCount`/`moduleCount` trong cache vẫn là số cũ tới
   hết TTL 30m trong khi dữ liệu thật đã đổi.

2. **Race trong `submit()`** — đọc lại `jobs.get(jobId)` sau `execute()` có thể thấy `SUCCEEDED`,
   làm response 202 báo sai trạng thái (và test `status == RUNNING` flaky). Trả object đã tạo.

3. **NPE tiềm ẩn** — `jobs.get(jobId).startedAt()` nổ nếu entry bị `evictOldJobsIfNeeded` xoá trước
   khi task chạy. Thêm null-guard.

4. **N+1 ở `GET /modules`** — `CatalogQueryUseCase.listModules` gọi `countQuestions` trong vòng lặp
   = 15 query cho 15 module. Thêm `ModuleRepository.countQuestionsByModule()` (1 query group-by).

### Regression bắt được giữa chừng (đáng ghi lại)

Sau khi sửa B-1 (token ordering trong `SearchText`), `JsoupContentSourceTest` đỏ ở
`searchKeywordsCarryVietnameseSynonymsDiacriticsAndAbbreviations`: nhóm synonym `garbage`/`gc` →
`thu-hoi-rac` không còn fire.

Nguyên nhân: bản sửa đưa `applySynonyms` lên **trước** khi collect answer, nên nhóm synonym mà từ
kích hoạt chỉ xuất hiện trong **câu trả lời** không bao giờ được phát hiện. Nhưng đảo lại (collect
answer trước) thì answer dài ăn hết budget 140 và n-gram tiếng Việt bị cắt — đúng thứ B-1 cần sửa.
Hai yêu cầu xung đột nếu chỉ collect answer một lần.

Fix: answer gom vào set riêng `answerTokens`; `applySynonyms(target, trigger)` nhận set trigger =
`title/tag tokens ∪ answerTokens`; synonym expand vào set chính; cuối cùng append `answerTokens` theo
budget còn lại. Thêm 2 test khoá hành vi này lại (`synonymsTriggeredOnlyFromAnswerAreStillExpanded`,
`longAnswerDoesNotCrowdOutTitleNgrams`).

Bài học: một finding "đúng" vẫn có thể phá một invariant khác. Test cũ bắt được vì nó assert **số
liệu cụ thể** (`thu-hoi-rac` phải có), không phải "search chạy được".

### m4b Frontend (2026-09-30)

**Status:** ✅ **SHIPPED** — `kg-frontend/` Next.js 14 App Router + TS + Tailwind

**Auth Flows** (3-step forgot-password shipped)
- Endpoints: `/login`, `/register`, `/forgot-password` (3-step email/code/reset)
- Google OAuth2: `POST ${API_BASE}/oauth2/authorization/google` → redirects to Google → `/auth/oauth2/success` reads `#accessToken=...`
- JWT in-memory; credentials include refresh token; `ensureAccessToken` coalesces refresh on stale token

**Content Browser**
- `GET /questions` — filters (moduleId, tag, difficulty) + search (full-text) + pagination
- `GET /questions/{id}` — renders `answerHtml` via `dangerouslySetInnerHTML` (⚠️ XSS risk noted — server-side Jsoup sanitize mitigates)
- `GET /topics`, `/modules` — navigate by topic/module

**Tech**
- CORS: `localhost:3000` + `PATCH` allowed (already backend CORS config m4a)
- Build/lint: ✅ clean

**Known XSS Vector (Design Decision)**
- FE renders `answerHtml` via `dangerouslySetInnerHTML` (React pattern, accepted risk). Mitigation:
  1. Backend **Jsoup sanitizer** strips `<script>`, `<iframe>`, `on*/`, `javascript:` URLs → stored clean
  2. Browser XSS prevention (CSP, HTTPOnly cookie) — partial defense
  3. Admin/import both sanitize → no stored XSS **from import or CRUD**
  - Residual risk: FE displaying user-controlled HTML via `dangerouslySetInnerHTML` is inherently unsafe without additional XSS framework

### Known Gaps / Deferred
- ~~**`GET /admin/content/jobs/{id}` chưa có.**~~ **FIXED** — `GET /admin/content/jobs/{jobId}`
  (ADMIN), 404 nếu hết hạn registry.
- ~~**`ImportContentUseCase` không transactional**~~ **FIXED** — `TransactionTemplate` bọc
  `execute()` trong `ContentImportJobService` (rollback khi fail).
- ~~**Client DOMPurify**~~ **FIXED** — `lib/sanitize-html.ts` + detail page.
- **TOCTOU ở pre-check `sortOrder`** — 2 request đồng thời cùng `sortOrder` đều pass check, kẻ thua
  nhận 409 generic. Chấp nhận; muốn message đẹp thì handle constraint name ở adapter.
- **Fixture `docs-mini/` bị copy 2 nơi** — nên gom vào `testFixtures` để tránh drift.
- **`QuestionRepositoryAdapter` chỉ persist `hints = null`** — cột có nhưng m4 không sinh.
- Job registry vẫn **in-memory** (max 20, mất khi restart, không share replica) — đủ 1 instance.

### Post-review fixes (ordinary review, không Bugbot/Security)

1. CORS `allowedMethods` thêm `PATCH` (m4b browser preflight).
2. Re-import xoá orphan sortOrder + test shrink (unit + Order 17 integration).
3. `ContentImportJob` port — presentation không còn phụ thuộc `infrastructure.content`; ArchUnit siết.
4. `POST /parse` integration test await job SUCCEEDED trước Orders 14–16.
5. `@Cacheable("modules")` trên `ModuleController.list`; bỏ `findModuleBySlug` dead code.
6. `truncateWellFormedHtml` — không xẻ giữa thẻ.
7. `sanitizeErrorMessage` — che absolute docs path trong job FAILED.

---

## 2026-09-30 — Deep review lần 2 + fix 2 BLOCKER

Review toàn bộ m4 (m4a + m4b) với bằng chứng đo trên hệ thống đang chạy, không chỉ đọc code.
`./gradlew clean build` xanh gồm test cũ.

### B-1 [BLOCKER] Search tiếng Việt không dấu trả 0 hit

Đo trên app thật (263 câu):

| Query | Trước | Sau |
|---|---|---|
| `q=khong dong bo` | **0** | **57** |
| `q=đồng bộ` | 57 | 57 |

Nguyên nhân: **index và truy vấn dùng hai không gian token khác nhau.**

- Index: `SearchText.addToken` loại token nếu `STOPWORDS.contains(token)` **hoặc**
  `STOPWORDS.contains(stripDiacritics(token))`. Với `"không"`: không nằm trong stopword, nhưng
  `"khong"` thì có → `return` sớm, dạng bỏ dấu **không bao giờ** được index. Index giữ `'không'`.
- Truy vấn: `plainto_tsquery('simple','khong dong bo')` → `'khong' & 'dong' & 'bo'`. Config `simple`
  chỉ lowercase + tokenize, **không biết hư từ tiếng Việt**, nên `khong` vẫn nằm trong truy vấn
  trong khi index không có token đó → 0 hit.

Ngoẩn cơ chế n-gram cũng không cứu được: `collectNgrams` lọc stopword **trước khi ghép cụm**, nên
`khong-dong-bo` không bao giờ được sinh.

Đáng chú ý: test cũ **pass do may mắn** — `q=bo nho` ra 97 hit, nhưng vì `to_tsvector('simple',
'bo-nho')` tách thành `'bo-nho','bo','nho'` và có tới 135 câu chứa token `bo` trần. Nghĩa là n-gram
không tạo phrase-match như thiết kế, chỉ kéo theo false positive. Test chỉ kiểm `q=heap` và
`q=bo nho` — hai query tình cờ pass, không có case nào chứa hư từ tiếng Việt.

**Fix:** `SearchText.normalizeQuery(String)` — bỏ dấu + loại hư từ, ghép lại bằng space; gọi trong
`QueryQuestionsUseCase.execute` trước khi xuống repository. Query rỗng sau khi lọc → `null`
(thành "không filter", không phải điều kiện không match gì).

**Quyết định kiến trúc:** normalize **không** đặt trong `QuestionQuery` compact constructor. Bản
đầu đặt ở đó, nhưng như vậy `domain.model` phải import `application` — sai chiều phụ thuộc Clean
Architecture. `QuestionQuery` giờ có `withQuery(String)` để application layer tạo bản đã chuẩn hoá;
record chỉ giữ trách nhiệm clamp tham số phân trang.

Test mới: `SearchTextTest` (+5), `QueryQuestionsUseCaseTest` (mới, 7 test), `QuestionQueryTest`
(mới), `ContentApiIntegrationTest` Order 18–19.

### B-2 [BLOCKER] `page` cực lớn → 409 "Resource already exists"

Đo trên app thật:

| Request | Trước | Sau |
|---|---|---|
| `?page=2147483647` | **409** `{"title":"conflict"}` | **200** items rỗng |
| `?page=0` / `size=0` / `size=-1` | 200 (đã clamp) | 200 (không đổi) |

Nguyên nhân: `setParameter("offset", (query.page() - 1) * query.size())` nhân hai số `int`.
`(Integer.MAX_VALUE - 1) * 20` tràn thành số âm → Postgres từ chối OFFSET âm → Spring bọc thành
`DataIntegrityViolationException` → `GlobalExceptionHandler` trả 409 generic vốn dành cho race
`uk_users_email`. Message hoàn toàn không liên quan tới tham số client gửi.

**Fix (3 lớp):**
1. `QuestionQuery.MAX_PAGE = 10_000_000` + clamp `page`.
2. `QuestionQuery.offset()` trả `long` (biên `int` vẫn đủ: 10^7 × 100 = 10^9 < 2^31).
3. `QuestionSearchDao` bind `query.offset()`.

**Không** thêm `@ExceptionHandler` mới cho `DataIntegrityViolationException` — 409 đó là hợp lệ cho
race đăng ký trùng email, chỉ cần đảm bảo tham số rác không đi tới đó nữa.

Test mới: `ContentApiIntegrationTest` Order 20–21.

### MAJOR còn mở (chưa fix — cần quyết định scope)

1. ~~**`flow-diagram` bị xoá mất nội dung thật.**~~ **FIXED**
2. ~~**FE không style class nội dung.**~~ **FIXED**

### Round 3 — đóng deferred còn actionable (2026-09-30)

- `GET /admin/content/jobs/{jobId}` + test Order 10/22
- Import bọc `TransactionTemplate`
- DOMPurify FE + synonym GC trùng gộp
- FE: Abort/debounce/refresh-blip (lần trước) + `.env` gitignore + `Difficulty` type

### Deferred còn lại

- Fixture `docs-mini/` copy 2 nơi → `testFixtures`
- Job registry in-memory (multi-replica cần Redis/PG)
- TOCTOU sortOrder pre-check → 409 generic under race (unique index vẫn bảo vệ)

### Đã kiểm và thấy đúng

Upsert idempotent; D5 không lộ đáp án (assert trên body thật); D7 context-path (test bằng
`RestTemplate` thuần, 404 vs 200); D8 SameSite fail-fast lúc startup; D11 Swagger + CSP có điều
kiện; RBAC 403; cache evict 3 cache; sanitizer dùng chung cho import **và** admin CRUD;
`refreshInFlight` coalescing ở FE.

**Bài học:** cả 2 BLOCKER đều lọt qua test suite xanh vì test search không có case hư từ tiếng Việt
và test paging không có case biên. Test "xanh" không đồng nghĩa endpoint đúng — phải assert bằng
**giá trị cụ thể từ dữ liệu thật**, và số liệu đo được phải được ghi lại để soi lại sau này.
