# Backend review theo góc nhìn sản phẩm người dùng cuối

Ngày: 2026-10-04. Baseline: `2ec214181f185f85a7217b5f84dcc7b03f22af00`.

## Kết luận

BE có nền tảng tốt cho sản phẩm học tập MVP: authenticated user scoping, ADMIN-only mutations, validation, transaction cho attempts/XP/SRS, search và moderation. Tuy nhiên, **không thể kết luận mọi chức năng đã đầy đủ hoặc được kiểm chứng trên prod**. Vấn đề quan trọng hơn tên endpoint là tính nhất quán giữa nội dung được phép học, phiên đang mở, lịch sử, lỗi và khả năng tiếp tục hành trình sau reload/mất mạng.

Đợt review này sửa các lỗi có thể tái hiện và kiểm chứng cục bộ. Không đổi JSON success contracts, không thêm migration, không thay quyền user, không reset dữ liệu học và không bật AI generation. Các sửa đổi này chưa commit/push/deploy trong đợt review.

### Evidence production — giới hạn cụ thể

- GitHub run [37199750868](https://github.com/darkb2001/knowledge-gym/actions/runs/37199750868), baseline `2ec2141`: build-test, publish-image, image-security, deploy-prod đều SUCCESS.
- Read-only `GET https://api.darkb-tech.io.vn/api/v1/actuator/health`: HTTP 200, `status=UP`, groups liveness/readiness.
- Đây là evidence vận hành, không phải authenticated user journey, mail delivery, xác nhận digest của container đang chạy, hay beta approval. Không tạo user/ghi dữ liệu trên prod để smoke test. Không suy ra các sửa local bên dưới đã lên prod.

## 1. Ma trận chức năng

| Hành trình | Endpoint tiêu biểu | Đánh giá từ code hiện tại | Evidence prod còn cần |
|---|---|---|---|
| Tạo tài khoản | `/auth/email-verification/request`, `/auth/register`, `/auth/verify-email` | Có OTP + xác nhận mật khẩu; luồng hai bước hợp lý nếu mail hoạt động. | Gửi/nhận OTP thật, resend/cooldown, lỗi SMTP/Kafka, OAuth callback. |
| Đăng nhập và bảo mật | `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/change-password`, `/auth/set-password` | Có refresh cookie, DB role/session guards, logout/reset invalidation, đổi mật khẩu khi đăng nhập và đặt mật khẩu cho OAuth. Giữ nguyên các commit mới ở baseline. | Cookie/CORS thực trên FE domain, multi-device revoke, stale JWT, blocked user. |
| Hồ sơ | `GET/PATCH /users/me` | Có profile/stats; sửa avatar URL sai thành lỗi input, không NPE. | Avatar upload/render và object-storage allowlist. Chưa có account deletion/anonymization. |
| Khám phá nội dung | `/tracks`, `/topics`, `/modules`, `/questions`, `/search` | Có catalog/search/filter/pagination. Sửa module questionCount để khớp PUBLISHED-only question list. | Cache eviction/search withdrawal latency; chính sách inactive topic/module. |
| Quiz | `/quiz/generate`, `/quiz/{id}`, `/quiz/{id}/submit`, `/quiz/history` | Có ownership, 1..50 câu, chỉ MCQ đủ option, submit transaction. Count là trần, không cam kết đủ số câu. Sửa final visibility check sau ranking. | Session/result recovery và nội dung/option revisions khi admin sửa lúc user đang làm. |
| Flashcard/SRS | `/srs/enroll`, `/srs/due`, `/srs/review/{cardId}` | Có SM-2 và attempts/XP atomic. Sửa ID enroll/due/review để không tiếp tục nội dung DRAFT/HIDDEN/ARCHIVED. | Full HTTP regression và race withdrawal/review; idempotency khi mạng retry. |
| Interview | `/mock-interview/start`, `/{id}/submit`, `/{id}/result`, `/history` | TEXT practice và tự đối chiếu đáp án, không AI chấm điểm. Sửa result để chỉ xem sample sau FINISHED. | Resume/autosave, reload/mất mạng, snapshots cho đáp án các câu bỏ trống. |
| Ghi chú/bookmark | `/notes`, `/notes/export`, `/users/me/bookmarks`, `/notes/{id}/convert` | Private ownership có sẵn. Sửa new links/conversion chỉ nhận published question; note cũ vẫn đọc/sửa được sau withdrawal. Sửa tags chứa null thành lỗi input. | Limits/pagination, module/question consistency, concurrent edits. |
| Blog cộng đồng | `/blog/posts`, `/comments`, `/like`, `/feed.rss` | Có public read, authenticated write, sanitation và moderation. | Pagination metadata, comment recovery/moderation experience, view-count semantics, spam controls. |
| Dashboard | `/users/me/stats`, `/users/me/progress`, `/dashboard/*`, `/mindmap` | Có analytics và learner feedback. Không coi heatmap/XP là bằng chứng chất lượng câu hỏi hoặc proficiency thực. | Timezone, withdrawal denominator, leaderboard privacy/blocked users, cache consistency. |
| Daily challenge | `/internal/challenge/daily` | **Chưa thực hiện: trả 501 sau token check. Không có learner-facing daily challenge flow.** | Cần phát triển hoặc loại khỏi scope/UI/cron quảng bá. |
| AI knowledge | `/admin/knowledge/*` | Review/materialize/publish là admin workflow, không phải consumer AI tutor. Learning generation vẫn fail closed. | Budget/evidence/provider/SSRF gates trong `26-beta-release-gates.md`. |
| Premium | Role `PREMIUM` | Không thấy entitlement/subscription/quota khác USER trong các learner flows đã đọc. Role không đồng nghĩa sản phẩm trả phí hoàn chỉnh. | Product scope + payment/entitlement nếu muốn bán. |

Ma trận là review contract và các hành trình chính; không phải chứng nhận bảo mật mọi endpoint hay kiểm thử E2E toàn bộ production.

## 2. Các lỗi đã sửa

### Visibility của flashcards không khớp nội dung public

`EnrollCardsUseCase` mode questionIds chỉ kiểm tra tồn tại; `QueryDueUseCase` đọc câu theo ID không xét status; `ReviewCardUseCase` vẫn ghi lịch/attempt cho thẻ đã rút nội dung.

- Enroll bằng ID từ chối toàn bộ request nếu có câu không PUBLISHED, trước khi insert card/deck.
- Due lọc status trước khi limit, không trả title/answer của câu chưa publish hoặc đã rút.
- Review bằng cardId từ tab cũ trả 404 nếu nội dung không còn PUBLISHED, trước khi thay schedule/attempts/XP.
- Không xóa thẻ/histories. Publish lại làm thẻ có thể xuất hiện theo lịch cũ.
- Đây là state validation ở application layer; chưa chứng minh linearizable với một admin withdrawal đồng thời sau lần đọc status. Không đổi tùy tiện câu hỏi trong quiz/interview session cũ.

### Module báo số câu không học được

`SpringDataQuestionRepository.countByModuleId/countGroupedByModule` đếm mọi status, trong khi learner question list chỉ PUBLISHED.

- Thêm countPublishedQueries và dùng chúng cho catalog list/detail.
- Giữ nguyên all-status counts cho `ManageCatalogUseCase.deleteModule`; module có toàn DRAFT không thể bị xem như rỗng rồi xóa.
- Có test chạy **Spring Data + Hibernate + disposable PostgreSQL**, không chỉ kiểm chuỗi SQL.

### Quiz vừa ranking xong có thể nhận câu đã ẩn

`GenerateQuizUseCase` kiểm tra lại status khi đọc ID sau ranking trước khi tạo session. Nếu tất cả selected content không còn khả dụng, trả 409 và không lưu phiên rỗng. Chưa coi đây là content revision/snapshot solution.

### Interview cho phép xem đáp án trước khi kết thúc

`GET /mock-interview/{id}/result` trước đây cũng trả sample cho ACTIVE. Nay trả 409 cho owner của ACTIVE, 404 cho người khác; FINISHED vẫn đọc được kết quả. Đây là bảo toàn trình tự practice, **không phải anti-cheat cho kỳ thi**: đáp án ngân hàng câu hỏi vẫn có learner detail route.

### Note có thể tạo liên kết/thẻ không học được

Tách check tồn tại (chỉnh note cũ) và check published (new link, đổi liên kết, convert). Không làm mất quyền sửa ghi chú cá nhân khi admin rút câu hỏi gốc. `tags:[null]` trả lỗi input trước khi insert/update thay vì NPE.

### Avatar URL sai trở thành lỗi server

`https:/avatar.png` có scheme hợp lệ nhưng thiếu host; trước đây `requireNonNull` ném NPE. Nay throw IllegalArgumentException (HTTP 400), đồng thời không nhận URL chứa credentials. Giữ kiểm configured storage prefix và khả năng xóa avatar bằng chuỗi rỗng.

## 3. Bất tiện và khoảng trống cần ưu tiên

### P0 — correctness/release gates

1. **Content revisions và session snapshots.** `QueryQuizUseCase` đọc question/options hiện tại; `SubmitQuizUseCase` chấm theo options hiện tại. `QuestionOptionRepositoryAdapter` giữ ID nếu unchanged hoặc đã được chọn trong quiz_answers, nhưng phiên chưa submit chưa có quiz_answers. Backfill/manual edit vẫn có thể thay đổi/xóa option hoặc đổi correctness khi user đang làm. Cần snapshot/version binding hoặc guard edits theo active-session membership. Interview unanswered result fallback cũng đọc answer hiện tại.
2. **Withdrawal policy toàn diện.** SRS/new note links đã được sửa. Cần quyết định rõ history vs ACTIVE quiz/interview, inactive taxonomy, notes riêng, search eventual consistency, cache và concurrency. Không sửa repository findById/findByIds thành global published-only: nó sẽ phá admin/history.
3. **Full-stack verification của patch** trên disposable stack/staging: real JWT, Redis, ORM, HTTP, cache/relay. Không dùng health=UP hay unit tests để bỏ qua gate.
4. **AI safety/accounting.** Chưa mở learning generation khi thiếu reserve/settle budget, evidence/schema/response quality limits và intake SSRF/DNS/redirect proof.

### P1 — hoàn thiện hành trình người dùng

- **Interview resume/autosave:** thêm session detail chỉ prompt + câu trả lời riêng, hỗ trợ lưu draft có optimistic version/idempotency. Hiện có history và result nhưng chưa có dedicated active-session recovery contract; FE persistence không được audit trong đợt này. Không tự khôi phục endpoint answer cũ đã được baseline gỡ.
- **SRS retry:** card lock ngăn lost update nhưng hai retry hợp lệ vẫn có thể review hai lần. Cần requestId/idempotency key, policy và tests; không chỉ debounce UI.
- **“Note → SRS” cần nói đúng:** hiện enroll câu hỏi được liên kết và gắn source_note_id. Due trả answer của question, không dùng nội dung note làm mặt sau. Hoặc gọi chức năng là “ôn câu hỏi liên quan”, hoặc phát triển authored front/back private cards. Không quảng bá là sinh flashcard từ mọi ghi chú.
- **Quiz availability:** trả capability/count số câu có MCQ trước khi user bấm bắt đầu; hiện generate có thể 409 “chưa có đáp án”. Cần content-quality gate cho distractors, không chỉ đủ hai options/một correct flag.
- **Notes/blog/comments pagination:** quiz/interview dùng PageResponse nhưng blog/notes/comments là list; không có cùng total/hasNext contract, notes/comments có thể không bounded. Thêm pagination tương thích hoặc version mới, không đổi array thành object bất ngờ làm FE hỏng.
- **Input và errors:** notes cần module/question consistency, limits/tags/content rules; chuẩn hóa malformed JSON/type errors/401/429. `SecurityConfig` entry point dùng error/message, advice dùng detail/title; `IllegalStateException` hiện echo message nội bộ. Client cần actionable errors, không host/provider detail.
- **Storage:** presigned PUT có user-scoped key nhưng chưa chứng minh quota, size/MIME validation, expiry/cleanup và scan policy. Không xem URL signing là upload hardening hoàn chỉnh.
- **Account lifecycle:** self-service deletion/anonymization, data export scope và session management UI cần product/privacy decision; notes.md không phải toàn bộ user-data export.

### P2 — không nên tăng scope chỉ để có thêm endpoint

- Daily challenge hiện 501: giữ fail-explicit; loại khỏi consumer feature list cho tới khi có assignment/answer/history hoàn chỉnh.
- PREMIUM hiện là role, không phải gói trả phí. Không bật paywall nếu chưa có entitlement + checkout + cancellation/refund semantics.
- Không quảng bá interview TEXT self-review là AI chấm điểm hay audio interview.
- Không coi AI admin pipeline là consumer chat/tutor. Ưu tiên chất lượng câu hỏi, recovery và consistency trước autonomous content generation.

## 4. Endpoint design có “chuẩn” không?

- UUID, authenticated principal thay vì body userId, ownership trả 404, ADMIN method security và transaction boundaries là hướng đúng.
- POST `/generate`, `/enroll`, `/submit`, `/convert`, `/finish` là application commands hợp lý; không cần đổi tên chỉ để REST thuần. Dễ hiểu, bounded, retry-safe và không mất history quan trọng hơn đổi động từ.
- 201 cho tạo session/card, 204 cho logout/delete, 400 cho input, 404 cho ownership/nonpublic content, 409 cho state conflict là hợp lý. Notes invalid links hiện giữ 400 tương thích contract cũ.
- Chưa có response/error/pagination conventions đồng nhất; chưa đủ evidence cho rate limits/quota/idempotency mọi mutation. Không tuyên bố production-complete.

## 5. Verification của patch

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  bash scripts/test-admin-platform.sh --local-postgres
# BUILD SUCCESSFUL: core 223, selected infrastructure 50, MVC 10; 0 failure/error/skip.

./gradlew :kg-presentation:test --tests '*AdminUsersHttpTest' --tests '*PresentationLayerArchTest' --console=plain
# Với JDK 21: BUILD SUCCESSFUL; 13 presentation tests gồm 3 architecture checks.

./gradlew build -x test --console=plain
# Với JDK 21: BUILD SUCCESSFUL, packaging/bootJar; KHÔNG phải full-test evidence.

git diff --check
# Pass.
```

- Core suite bao gồm DomainLayerArchTest và regression mới cho publication, preservation, sample-answer gating, note input và avatar validation.
- ModulePublicationCountsTest chạy thật Flyway/Spring Data/Hibernate/PostgreSQL cho raw/published counts và JDBC published note lookup.
- SrsApiIntegrationTest và QuizApiIntegrationTest đã thêm HTTP regression, **compile nhưng chưa thực thi local**: Docker daemon probe timeout sau 8s. Không sửa test để né Testcontainers hoặc dùng production DB.
- LSP probes cuối: 5 catalog/counts/notes files + 4 learning/profile/wiring files confirmed clean, không error. Probe đầu có 3 inconclusive; active re-probe sau build đã xác nhận sạch. Gradle compile/tests cũng pass.
- Logs: `/tmp/kg-user-product-review-final.log`, `/tmp/kg-product-review-arch.log`, `/tmp/kg-product-review-package.log`.

Trước khi push/deploy: chạy full CI `./gradlew build` trên runner có Docker và staging smoke cho các contracts đã đổi. Beta decision vẫn theo `26-beta-release-gates.md`.
