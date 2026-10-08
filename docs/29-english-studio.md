# English Studio — VSTEP-aligned practice room

## Implementation checkpoint

Built against backend `9a6124c` and frontend `8d4d941`, both fetched and fast-forwarded without overwriting local work. This feature is implemented locally; this checkpoint does **not** claim a push, deployment, official VSTEP accreditation, or public-beta approval.

The authenticated frontend route is `/english`, linked as **Phòng học tiếng Anh / English Studio**. English practice is a separate bounded context, not an IT question bank, SRS enrolment, interview score or XP source.

## Standards and scope

Source checked: [ULIS-VNU VSTEP test format](https://vstep.vnu.edu.vn/test-format/).

| Skill | Published VSTEP.3–5 format | Initial practice coverage |
| --- | --- | --- |
| Listening | Approximately 40 minutes; 3 parts; 35 MCQs | 3 original audio mini-tasks: announcement, conversation, short talk; 3 questions each |
| Reading | 60 minutes; 4 passages; 40 MCQs; 1900–2500 total words | 1 original short passage with 4 questions; **not** a complete reading paper |
| Writing | 60 minutes; letter/email ≥120 words (1/3), essay ≥250 words (2/3) | Both task types, editable draft, word count, save/resume and self-review checklist |
| Speaking | 12 minutes; social interaction, solution discussion, topic development | All 3 parts; microphone recorder, playback/download, persisted text reflection |

These are nine original practice exercises, not official sample papers or a complete mock exam. Their difficulty has not been calibrated to certify B1/B2/C1. Objective feedback is a raw correct-answer count, never a VSTEP grade. Writing and speaking are **self-review**, with no AI grading or pronunciation evaluation.

Listening MP3s are actual bundled, original synthetic speech assets, generated with the build-time-only eSpeak-NG Emscripten engine. Conversation audio alternates two synthetic voices. The engine is not bundled or loaded at runtime; generation is reproducible through the frontend script and audio provenance manifest. Plain-text script copies are in docs, not served as frontend public transcript assets.

## API (existing /api/v1 context prefix applies)

All routes require normal authenticated access. The controller also has `@PreAuthorize("isAuthenticated()")`. Actor identity is exclusively `@AuthenticationPrincipal UUID`; no request accepts a user ID or claimed score.

- `GET /english/exercises`: public-facing exercise DTOs; excludes correct choices, explanations and listening transcripts.
- `POST /english/attempts`, `{exerciseId}`: starts or resumes the owner's one draft for that exercise.
- `GET /english/attempts?page=1&size=10`: owner-scoped, bounded history.
- `GET /english/attempts/{id}`: owner-scoped draft/submitted response; another user's ID returns 404.
- `PUT /english/attempts/{id}`: `{version, answers, response, elapsedSeconds, submit}`.

Answers map original question IDs to zero-based option indices. The server validates IDs/ranges and requires every objective answer before final submission. Subjective submission requires nonblank text; a short practice response can be submitted below the suggested word count.

Draft feedback is null. Only submitted attempts receive server-calculated objective feedback, explanations and transcript. Subjective feedback contains no numeric grade.

## Persistence and concurrency

Migration `V038__english_practice.sql` adds `english_attempts` (50 application tables including upstream migrations, excluding Flyway history).

- Owner foreign key, constrained DRAFT/SUBMITTED state, nonnegative version.
- Unique partial index: one DRAFT per user/exercise.
- Creating/resuming serialises on the owner row; quota checks and insert run in one transaction.
- Limits: 1000 retained attempts/user; 20,000 text characters; 40 answer entries; elapsed time 0–7200 seconds; history size 1–50/page.
- Conditional owner/state/version UPDATE prevents cross-user updates, stale-tab overwrites and edits to submitted work.
- An identical final PUT retry at the original version returns the submitted attempt without a second write. A changed payload conflicts.
- Submission is irreversible for that attempt. A subsequent start creates a new draft; previous submitted history is retained.
- No changes to XP, IT questions, quiz/SRS/interview sessions or their history.

Exercise IDs end in `-v1`. Released IDs/content/answer keys must remain immutable; author a new ID for a revision and retain old catalog entries to read existing history. This is an authored catalog convention, not an admin revision-management/snapshot engine.

## Client behaviour and privacy

- Existing RequireAuth, session-generation/logout fences, bilingual locale and day/night theme remain in use.
- Deep link `/english?attempt=<id>` restores only a server-owned saved attempt.
- Save is **explicit**, not offline persistence or autosave. Failed saves retain in-memory text; conflict UI asks learners to copy text and reopen the latest server state.
- Unsaved text and undownloaded clips trigger warnings on leaving the workspace, internal navigation links and browser unload where the browser permits.
- Timer is an optional suggested practice clock, pauses/resumes, and never auto-submits. It is not an exam-enforcement clock.
- Recording starts only after a click and microphone permission, stops at five minutes, releases tracks on stop/unmount, and revokes blob URLs.
- Pending permission can be cancelled; a late stream after cancellation/unmount is stopped.
- Clips stay **only in the current tab**. Download to retain them. The backend stores only speaking reflection text; it does not upload, persist or transcribe audio.
- Writing/reading/feedback are plain text/native inputs, not unsanitized HTML.

## Verification

Backend:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  bash scripts/test-english-room.sh --local-postgres
```
Result: **BUILD SUCCESSFUL**; 258 core tests, 17 selected migration/persistence tests, 9 MVC/architecture tests; zero failures/errors/skips. The new PostgreSQL test applies real Flyway migrations and checks ownership, one-draft resume, concurrent start, optimistic UPDATE, submitted immutability, history pagination, JSON roundtrip and unchanged XP. MVC tests exercise security/serialization with a mocked application service, not deployed JWT/Redis acceptance.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build -x test --console=plain
```
Result: **BUILD SUCCESSFUL** (packaging only, not additional test evidence).

Frontend:
```bash
npm run build && npm run typecheck && npm run lint && npm test
NODE_PATH=/tmp/kg-english-browser/node_modules KG_UI_URL=http://127.0.0.1:3214 \
  node scripts/verify-english-room.mjs
```
Build/typecheck/lint passed; **230 tests / 35 files** passed. Browser fixtures: **71 checks / 18 captures**, zero JS errors, unmatched API requests, horizontal overflow or automated WCAG A/AA violations. Viewports: 1440×1000, 390×844, 844×390. Actual static MP3 decode and real MediaRecorder encoding/download with Chrome's fake microphone are exercised. API writes are all intercepted synthetic fixtures.

Resolved checks during implementation:
- Initial infrastructure compile used Jackson 2 imports; corrected to the upstream Boot 4/Jackson 3 API and constructor-safe row mapper.
- Migration table-count assertion expected 49; updated to 50 for V038 and reran successfully.
- Initial FE typecheck referenced obsolete .next routes from before the fast-forward; fresh build regenerated types.
- Recorder cleanup-counter lint advisory was fixed by capturing the stable counter ref.
- Browser harness initially needed temporary Playwright/axe dependencies, a targeted audio selector (global lofi also has an audio element), and awaiting the asynchronous reopen response.
- Early full-page captures retained a scrolled sticky rail/focused skip link; final captures reset to document top and clear temporary focus before capturing.
- Mechanical design detector ran once and returned `[]`.
- Active LSP checks still reported an EnglishRoom module-resolution error inconsistent with successful Next build/tsc (new file exists), a deprecated legacy beforeunload fallback hint, and Vietnamese spellchecker false positives. Do not treat the LSP checkpoint as completely clean. The recorder's unnecessary client-entry directive was removed so callback props inherit the enclosing client boundary.

## Pre-push review

Owner requested review, then commit/push of all changes. Direct review covered principal-only ownership, answer-key exposure, optimistic writes, submitted immutability, bounded inputs/quota, audio provenance, recorder cleanup and client navigation/error recovery.

Two frontend defects were fixed: retry now clears an unavailable attempt deep link; opening another attempt locks the previous workspace against edits/saves until the request completes. Both have browser regressions at all three viewport sizes. The fresh review run passed 258 core + 17 selected infrastructure + 9 MVC/architecture tests; FE build/typecheck/lint and 230 tests passed; browser fixtures passed 71 checks with zero JS/API-match/WCAG A/AA failures. No live production acceptance is inferred.

## Remaining release work

1. Live BE+FE authenticated acceptance with real cookies/JWT/DB/Redis and production CORS, not the fixture harness.
2. Real microphone, playback/download and permission behaviour on iOS Safari/Android; the fake device is not that evidence.
3. Full Docker-enabled regression CI and staged V038 migration/backup/rollback validation.
4. Educational review and calibrated content expansion. Full timed mock exams, exam scoring, teacher/AI feedback and audio persistence are not implemented here.
5. Content authoring/admin/revision tooling and account/history retention/deletion policy; the bounded initial quota has no automatic purge.
6. Existing shared UtilityBubble can overlap long mobile headings at the viewport's bottom-right. The shared utility was not redesigned in this feature; this is a remaining app-shell polish item.
7. No production secrets, feature flags, permissions or deployment settings were changed.
