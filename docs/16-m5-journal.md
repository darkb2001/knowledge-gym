# Knowledge Gym — m5 SRS + SM-2 + FlashcardDeck Journal

## 2026-10-01 — m5 Complete

### Scope (✅ done)
Spaced Repetition System: SM-2 domain service thuần Java, enroll/due/review endpoints ghi `study_attempts`
cùng transaction, và FE flashcard flip + tự chấm 4 mức.

- **`Sm2Scheduler`** — domain service thuần Java (`final class`, private ctor, static methods). Không
  Spring/JPA (ArchUnit `DomainLayerArchTest` enforce). Input là thang API 0–3, **không** phải SM-2 gốc 0–5.
- **`SRSCard` / `SrsDeck` / `StudyAttempt`** aggregates + `AttemptSource` enum.
- **Dual-mode `/srs/enroll`** — Mode A (`moduleId`, auto deck theo module), Mode B (`questionIds[]`, custom deck).
- **`/srs/due`** — đọc theo composite index `idx_srs_due (user_id, next_review)`.
- **`/srs/review/{cardId}`** — SM-2 + insert `study_attempts` **cùng tx**.
- **FE `FlashcardDeck`** — flip 3D, 4 nút Again/Hard/Good/Easy → quality 0/1/2/3 + `timeMs` đo từ lúc
  hiển thị thẻ; `app/flashcard/[moduleId]/page.tsx` enroll mode A rồi lấy due list.

### Mapping contract (canonical — copy từ plan, giữ nguyên số liệu)

| API quality | Nhãn FE | interval | ease | repetitions |
|---|---|---|---|---|
| 0 | Again | 1 ngày | −0.2 | 0 (reset) |
| 1 | Hard | ceil(prev × 1.2) | −0.14 | +1 |
| 2 | Good | ceil(prev × ease) | giữ nguyên | +1 |
| 3 | Easy | ceil(prev × ease × 1.3) | +0.1 | +1 |

Ease floor **1.3**; first review (`repetitions == 0`): Again/Hard/Good → **1 ngày**, Easy → **4 ngày**
(lịch Anki cho thẻ mới — công thức nhân với `interval = 0` sẽ ra 0). `next_review = today + interval_days`.

`study_attempts.is_correct = (quality >= 2)` — Again/Hard = `false`, Good/Easy = `true`. Đây là quy ước
của m5 (schema bắt `is_correct NOT NULL` và không có field nào khác để suy ra), ghi rõ để m7/m12 không
diễn giải lại khác.

### Architecture Compliance
- `kg-core/learning/domain/**` **Java thuần** — không Spring/JPA; `Sm2Scheduler` không đọc đồng hồ hệ thống
  (nhận `today`/`reviewedAt` qua tham số) nên test được bằng `Clock.fixed`.
- `kg-presentation` không import `infrastructure.persistence`. Bằng chứng đáng chú ý: `SrsApiIntegrationTest`
  muốn assert row `study_attempts` nhưng không được inject `SpringDataStudyAttemptRepository` (ArchUnit
  import cả test classpath) → đọc bằng JDBC thuần qua `DataSource`. Hoá ra đây là điểm mạnh: dữ liệu được
  verify thẳng từ DB, không đi qua port ghi mà sản phẩm đang kiểm tra.
- `userId` luôn lấy từ JWT principal (`@AuthenticationPrincipal UUID userId`), không bao giờ từ body/query.
- **Không migration mới** — V004 đã đủ (`srs_decks`, `srs_cards`, `study_attempts` + index + UK). `ddl-auto=validate`
  vẫn xanh.

### Files Created

**kg-core** — `com.knowledgegym.learning`
- `domain/model/`: `SRSCard`, `SrsDeck`, `StudyAttempt`, `AttemptSource`
- `domain/service/`: `Sm2Scheduler`
- `domain/port/`: `SRSCardRepository`, `SrsDeckRepository`, `StudyAttemptRepository`
- `application/`: `EnrollCardsUseCase`, `QueryDueUseCase`, `ReviewCardUseCase`
- tests: `Sm2SchedulerTest` (canonical table từng dòng), `SrsLearningModelTest` (`SrsDeck`/`SRSCard`),
  `EnrollCardsUseCaseTest`, `QueryDueUseCaseTest`, `ReviewCardUseCaseTest`, `LearningTestSupport` (in-memory fakes)

**kg-infrastructure**
- `persistence/adapter/`: `SRSCardRepositoryAdapter`, `SrsDeckRepositoryAdapter`, `StudyAttemptRepositoryAdapter`
- `SpringDataSrsCardRepository.insertIgnoringDuplicates` (native `ON CONFLICT (user_id, question_id) DO NOTHING RETURNING id`)
- `SpringDataSrsDeckRepository.findByUserIdAndModuleId`

**kg-presentation**
- `rest/srs/`: `SRSController` + `dto/EnrollRequest`, `ReviewRequest`, `SRSCardDTO`, `SrsResponses`
- `SrsApiIntegrationTest` (Postgres + Redis Testcontainers, 20 test theo `@Order`)

**kg-frontend**
- `lib/srs.ts` (types + `enrollModule`/`enrollQuestions`/`listDue`/`reviewCard`/`QUALITY`)
- `components/FlashcardDeck.tsx`
- `app/flashcard/[moduleId]/page.tsx`

### Files Modified
- `content/domain/port/QuestionRepository` — thêm `findByIds(Collection<UUID>)`
- `QuestionRepositoryAdapter` — implement `findByIds` (1 query, tránh N+1 khi lấy nội dung due queue)
- `UseCaseConfig` — bean `Clock`, `EnrollCardsUseCase`, `QueryDueUseCase`, `ReviewCardUseCase`
- `app/questions/page.tsx` — nút "Ôn flashcard module này →" (hiện khi đã chọn module) + đọc `?moduleId=` +
  bọc `Suspense` (bắt buộc khi dùng `useSearchParams`)
- `app/globals.css` — `.flow-node.secondary` dùng `border-ink-600` (Tailwind không có `ink-500`, gây syntax error)

### Quyết định quan trọng khi implement

1. **`QueryDueUseCase` lọc/sắp/limit ở application, không đẩy xuống SQL.** `moduleId` nằm ở `questions`,
   không phải `srs_cards`, nên `LIMIT` ở SQL sẽ cắt trước khi lọc module → trả thiếu thẻ. Thứ tự đúng là
   filter module trước, cắt limit sau, sắp theo `nextReview`. Hệ quả: `findDue` trả về **tất cả** thẻ đến hạn
   rồi mới cắt — chấp nhận được ở MVP, nhưng là điểm cần revisit khi số thẻ/user lớn (xem Risk Assessment).

2. **Review ghi `study_attempts` trong cùng tx (phương án (a) của plan).** m7 cộng XP/mastery từ bảng này;
   nếu update card thành công mà insert attempt thất bại, người học mất tiến độ "đã ôn" trong khi lịch ôn
   vẫn nhảy — sai lệch âm thầm không sửa lại được.

3. **`timeMs` nullable, KHÔNG default 0.** Analytics m7 phải phân biệt "không đo" (NULL) với "đo được 0 ms".
   FE m5 luôn gửi; client khác (CLI test) có thể không.

4. **Enroll idempotent quan sát được từ client.** `enrolled` là số thẻ **mới**, không phải số id gửi lên —
   retry không được thấy con số khác nhau giữa hai lần gọi. Re-enroll cũng **không reset lịch ôn** của thẻ
   đã có (`ON CONFLICT DO NOTHING`, không `DO UPDATE`).

5. **Deck theo module được reuse ở tầng application.** V004 không có UK `(user_id, module_id)`, nên
   `findByUserIdAndModuleId` là hàng rào duy nhất. Race nhỏ (2 request đồng thời tạo 2 deck cùng module)
   được chấp nhận: UK ở `srs_cards` vẫn đảm bảo không sinh thẻ trùng, chỉ dư 1 deck rỗng.

6. **Invariant `SrsDeck.is_custom == (moduleId == null)` enforce ở `rehydrate`, không chỉ factory.**
   Mọi factory đều suy `is_custom` từ `moduleId` nên row lệch chỉ lọt vào qua đường đọc DB — validate ở
   `rehydrate` làm row lệch nổ ngay khi đọc thay vì âm thầm lan vào luồng enroll.

7. **Thẻ "mồ côi" bị bỏ qua, không trả DTO rỗng.** Re-import xoá câu là chuyện bình thường; `QueryDueUseCase`
   filter theo `questionRepository.findByIds` nên FE không bao giờ nhận thẻ không render được.

8. **Chỉ một keyboard listener ở `window`.** Gắn thêm `onKeyDown` trên khung thẻ sẽ khiến Space/Enter toggle
   hai lần. Map phím: `1–4` theo index trong `RATINGS` (khớp `quality`), hoặc `A/H/G/E`. Test tay xác nhận
   phím `2` → Hard, `4` → Easy.

### Verification

| Kiểm tra | Kết quả |
|---|---|
| `./gradlew build` (ArchUnit + Testcontainers) | ✅ BUILD SUCCESSFUL |
| `Sm2SchedulerTest` | ✅ từng dòng bảng canonical, gồm Again ≠ Hard và ease floor |
| `SrsApiIntegrationTest` (20 test) | ✅ Postgres + Redis thật |
| `npx tsc --noEmit` | ✅ clean |
| `npx next lint` | ✅ no warnings/errors |
| `npm test` (vitest) | ✅ 6/6 |
| API E2E (curl) | ✅ enroll 19 thẻ → due → Good(4200ms) → interval 1; Easy → interval 4; quality 4 → 400; `study_attempts` = `FLASHCARD/t/4200/answer NULL` |
| Browser E2E | ✅ login → enroll → flip (đáp án HTML render) → phím `4` → phím `2` → màn "Hết thẻ trong phiên" tally 17 Hard / 1 Good / 1 Easy |

### Số liệu
- 19 thẻ enroll từ module `01-java-core` (19 câu, 1 deck).
- `POST /srs/review` Good lần 1 → `intervalDays: 1`, `easeFactor: 2.5`, `repetitions: 1`, `nextReview: +1 ngày`.
- Easy lần 1 → `intervalDays: 4`, `easeFactor: 2.6`.

### Ghi chú cho phase sau
- **m7** dùng lại `StudyAttempt` (domain model + port + adapter + entity đã có sẵn từ m5) — chỉ cần thêm
  `UserProgress` wiring, không định nghĩa lại attempt. Đã sửa `mini-phase-07-dashboard.md` bước 1 cho khớp.
- **m8** (`/notes/{id}/convert`) dùng `srs_cards.source_note_id` — cột đã có, hiện luôn NULL.
- `GET /srs/stats` và `POST /srs/reset` (trong `08-rest-api.md`) **chưa** implement — thuộc m7.

## 2026-10-01 — Follow-up: xử lý review thứ hai (m5)

Review thứ hai chạy trên snapshot **trước** các fix m1/m2/M1, nên hai finding MAJOR của nó đã có sẵn
trong code hiện tại (không phải làm lại):

| Finding | Trạng thái thực tế |
|---|---|
| **M1** — `questionIds` không cap + N+1 `findById` mỗi id | **Đã fix**: `MAX_QUESTION_IDS = 500` (`EnrollCardsUseCase`) + `requireQuestionsExist` dùng 1 query `findByIds`, dò id thiếu trong bộ nhớ. Test: `questionIdsBeyondCapAreRejected`, `questionIdsAtCapAreAccepted` |
| **M2** — review đồng thời mất cập nhật (không optimistic lock) | **Đã fix**: `@Lock(PESSIMISTIC_WRITE)` `findByIdForUpdate` + `ReviewCardUseCase` đọc kèm khoá. Test `findByIdForUpdateBlocksWhileAnotherTransactionHoldsTheRowLock` (có negative control) |

Finding còn mở đã xử lý trong pass này:

- **n1 (bug thật)** — `{"questionIds":[null]}` → `List.copyOf` ném `NullPointerException` → **500**.
  `dedupe` giờ chặn null tường minh → 400 (`IllegalArgumentException`). Test `nullQuestionIdElementIsRejectedAsBadRequestNotServerError`.
  Lưu ý: không dùng `contains(null)` vì `ImmutableCollections.contains(null)` cũng ném NPE — dùng `stream().anyMatch(Objects::isNull)`.
- **m1 (deck membership)** — chọn hướng "document" thay vì backfill: `ON CONFLICT DO NOTHING` không ghi đè
  thẻ cũ, nên thẻ enroll trước ở mode B giữ `deck_id = NULL` dù response trả `deckId`. Đã ghi rõ trong javadoc
  `EnrollCardsUseCase`: `deckId` là thông tin, **không** phải nguồn chân lý cho membership; view lọc theo deck
  phải đọc `srs_cards.deck_id`. Chưa có màn deck ở m5 nên backfill là YAGNI.
- **m5 (FE keyboard)** — Space/Enter trước đây bị `preventDefault` trên toàn trang (chặn cả cuộn trang) và
  cướp phím của link/button. Giờ bỏ qua khi focus ở `INPUT/TEXTAREA/SELECT/BUTTON/A`.
- **n2** — thêm `SRSCardRepositoryAdapterTest` cho `toPgUuidArrayLiteral` (boundary sát native query, trước đó không có test).

Đóng test gap #1 của review: `enrollModuleCreatesCardsForEveryQuestionAndOneDeck` giờ đếm `srs_decks` thẳng
trong DB (`deckCountForUser`) để chứng minh vế "không deck trùng" của criterion 4 — `enrolled: 0` chỉ chứng
minh vế thẻ. Đã kiểm chứng bằng negative control (đổi kỳ vọng sang 2 → đỏ đúng chỗ).

Các gap còn lại (chấp nhận, không làm ở m5): test cho mode B `deckId` ghi vào `srs_cards.deck_id` ở DB,
invariant `is_custom` ở đường đọc adapter, và FE component test cho flip/keyboard. Không phải regression.

### Verification (lượt follow-up)
| Kiểm tra | Kết quả |
|---|---|
| `./gradlew clean build` | ✅ BUILD SUCCESSFUL (40s) |
| `EnrollCardsUseCaseTest` | ✅ 20 test (gồm null-element + cap) |
| `SrsApiIntegrationTest` `--rerun-tasks` | ✅ xanh, gồm assert đếm deck |
| `SRSCardRepositoryAdapterTest` (mới) | ✅ |
| `npx tsc --noEmit` | ✅ clean |
| `npm test` (vitest) | ✅ 6/6 |

## 2026-10-01 — Review lần 3 + fix FE flip

Re-review trên code hiện tại (không phải snapshot cũ). Backend SRS **không có finding mới** — SM-2,
dual-mode enroll, cùng-tx review+attempt, IDOR, khoá bi quan, cap mode B đều còn đúng.

**MAJOR (FE)** — `FlashcardDeck` comment nói hai mặt `absolute` nhưng thực tế là flow siblings:
sau khi lật, mặt đáp án nằm **dưới** khung `min-h-[22rem]` (viewport trống). Fix: cả hai mặt
`absolute inset-0`, scroll trên từng mặt.

**MINOR đã fix**
- `listDue({ moduleId, limit: 100 })` — trước đó mặc định BE 20 cắt im phiên module lớn
- Phím 1–4/A/H/G/E chấm được cả khi chưa lật (khớp nút chuột + hint text)
- JSDoc `timeMs` trong `lib/srs.ts`: "hiển thị" thay vì "lật"
- Empty-state copy bỏ nhánh `created > 0` chết (thẻ mới luôn due hôm nay)
- HTTP test Order 20: `{"questionIds":[null]}` → 400

### Verification
| Kiểm tra | Kết quả |
|---|---|
| `npx tsc --noEmit` + `npm run build` | ✅ |
| `SrsApiIntegrationTest` `--rerun-tasks` (20 test) | ✅ |

