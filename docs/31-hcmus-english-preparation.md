# English Studio — HCMUS preparation: historical 39-entry checkpoint

**Historical evidence:** this checkpoint preceded the committed/pushed 39-entry release (`4936085` backend / `6013889` frontend). Current 47-entry work, opt-in transcripts and recent UIT research are documented in [32-english-transcript-and-recent-sources.md](32-english-transcript-and-recent-sources.md). Counts and local-only statements below describe the earlier checkpoint, not the current release status.

Date: 2026-10-10. User confirmed KHTN in Ho Chi Minh City. No commit/push/deployment requested or performed. Supersedes the blocked audio checkpoint in docs/30-vstep-content-review.md, not its historical research caveats.

## Official-source conclusion

HCMUS internal master's English entrance format in the **2026 second-round notice, Appendix 5**, differs from VSTEP.3–5. Vocabulary/Reading: 10 + 10 questions / 20 points. Grammar/Use of English: 15 completions + 10 cloze + 5 transformations / 40 points. Listening: 10 short conversations + 5 long-conversation questions + 5 talk questions / 20 points. Speaking: introduction (5) and guided conversation (15). Published total threshold 50/100, no individual-part fail mark; missing a scheduled component invalidates completed components under the notice. Do not turn app raw correct counts into a pass/official grade.

The appendix does not publish an English topic bank. Appendix 6 is **subject-specialist admission interviewing**, not English Speaking. Study/work/day-to-day topics in the product are original practice choices, not recalled/predicted examination questions.

Source: https://sdh.hcmus.edu.vn/2026/08/05/thong-bao-xet-tuyen-chuong-trinh-dao-tao-trinh-do-thac-si-nam-2026-dot-2/ . Linked DOCX was downloaded and XML text extracted; the older scanned 2022 PDF was not used to infer current format. Full source links, scheduling ambiguity (90/about20/about15 versus 120-minute maximum), certificate exemptions and HUS distinction: `../knowledge-gym-frontend/docs/english/KHTN.md`.

## Implemented

- **39 original entries:** 31 VSTEP tasks, eight HCMUS preparation tasks. Composite sets reuse constituent material; not 39 independent papers.
- HCMUS vocabulary10, grammar15, cloze10, five transformation prompts, introduction/guided speaking, ten short conversations, a five-question long conversation, and a **20-question grouped Listening set / 10 + 5 + 5**. Original synthesized audio, not an institution recording or calibrated mock. Reading/shared standalone Listening carry explicit transfer-practice labels.
- Target selector and `curriculum` metadata. HCMUS task types sort ahead of transfers. Native “Ngữ pháp & Viết” navigation/copy avoids essay-only implications.
- `/english?track=hcmus` preserves target through open/save/close/reload and shared-task deep links. Unrelated historical VSTEP tasks restore their own track; no new owner/profile/security authority is inferred from URL parameters.
- Complete VSTEP Reading40/2,151 words and Listening35; section selector retains global answer IDs/numbering.
- **16 full reference responses**, bilingual notes, owned submitted attempts only. No catalog/draft models, keys, explanations or transcripts; no subjective numeric grade. HCMUS transformations are self-review, not exact-string auto-marking.
- Vocabulary20×8=160/158 distinct, five modes, owner-tab queues/location/known/review reload metadata, no cross-device/server XP/SRS/mastery. Assisted retries stay review, no unsaved private sentence persistence. Mobile labels fully discoverable.
- **171 build-time neural MP3s:** 160 phrase files + nine standalone Listening clips + two composites. Atomic per-clip checkpoint, pinned input/output hashes, lazy Kokoro model, verified-cache resume. VSTEP composite seven clips / 839.805sec; HCMUS composite three clips / 425.343sec. No repeated speech synthesis or artificial padding to examination guidance time.
- Original nine-exercise constructor byte-identical to HEAD. Protected-block SHA-256 `027812b6bb763a76cabd572715c1f6d55788e00c9f0dd4918f284f071f5d6de1` (block includes declaration/closing punctuation; different extraction boundaries from prior doc30 hash).
- Compiled authoring fixture under ignored backend build output; FE `.fixtures/english/` is a versionable test artifact containing **original** keys/models. It is not application code/public assets/API. Browser harness explicitly whitelists public fields.
- Reusable ForumFlash research ran successfully: 20 CSV decks/4,000 rows,488 distinct phrases,3,512 repeated rows,490 exact examples,596 phoneme tokens/zero blank joins. No full source bank/definitions/examples/IPA/audio import or reuse-licence claim.

## Exact verification commands and outcomes

Backend (`knowledge-gym`):
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  bash scripts/test-english-room.sh --local-postgres
```
Output: `BUILD SUCCESSFUL in 15s`, `17 actionable tasks: 17 executed`, disposable PostgreSQL stopped. XML totals: **kg-core282 + infrastructure17 + presentation13 =312 tests**, zero failures/errors/skips. Includes all objective catalog entries submit validation, full-set counts/evidence/group IDs, complete model coverage, HTTP ownership/secrecy and PostgreSQL/Flyway persistence checks. Not full Docker/Testcontainers regression.

Frontend (`knowledge-gym-frontend`):
```bash
node scripts/sync-english-test-fixture.mjs
KG_KOKORO_MODELS=/tmp/kg-kokoro-model KG_KOKORO_PYTHON=/tmp/kg-kokoro-env/bin/python \
  node scripts/generate-english-audio.mjs
python3 scripts/test-english-audio-build.py
npm run lint && npm run typecheck && npm test && npm audit --omit=dev && npm run build
```
Output: `Synced 39 authored test entries`; generation passed (new HCMUS clip+composition approximately92sec, subsequent cached rerun passed); Python `Ran 6 tests ... OK`; eslint `--max-warnings=0` and TypeScript no-emit exited0; **38 files / 705 tests passed**; audit `found 0 vulnerabilities`; production Next15.5.27 compilation/static generation passed. All **171 MP3s decoded successfully** using `ffmpeg -v error -i <each asset> -f null -`; all output hashes/text hashes/composite-source hashes checked.

Built local Next server, browser fixture confirmation:
```bash
NODE_PATH=/tmp/kg-english-browser/node_modules KG_UI_URL=http://127.0.0.1:3226 \
  KG_UI_CAPTURE=0 KG_UI_CAPTURE_TARGETS=hcmus-guide,hcmus-listening,hcmus-grammar \
  node scripts/verify-english-room.mjs
```
Output: `checks:222`, `screenshots:48`, `errors:[]`, `accessibilityViolations:[]`; unmatched API requests0. 1440×1000/390×844/844×390; all five vocabulary modes/queue reload/privacy/storage-denial simulations; audio error/retry/rate/rewind/decode; late microphone cleanup/download/no upload; failed save retaining text/conflict/reload;40 Reading answers;20 HCMUS Listening answers with10/5/5 sections; grammar15; every reference model; VI/EN/light/dark; native and transfer target restoration; owner-change and logout-event metadata fencing. Final pass refreshes only the three HCMUS capture types, retaining the prior complete visual batch for unchanged screens; bounded verification, not an open-ended redesign loop. Summary: FE `docs/english/browser-verification.json`.

Impeccable:
```bash
/Users/bao2k1/Documents/javaNote/.pi/skills/impeccable/scripts/impeccable detect --json \
  components/english/EnglishRoom.tsx components/english/VocabularyStudio.tsx \
  components/english/ReferenceResponse.tsx components/english/ExamPreparationGuide.tsx
```
Output `[]`, exit0. `git diff --check` in both repositories: no output, exit0.

### Failures encountered and corrected, not hidden

- Intermediate backend count test referenced `questionIds()` instead of the actual `itemIds()`: two `cannot find symbol` compiler errors. Corrected both Java and FE test accessor; complete backend rerun passed.
- After extending36→39 entries, sync/browser fixture guards correctly rejected stale expected36 (`Expected the compiled 36-entry original catalog`, `39 !== 36`). Updated exact expectations to39, preserved uniqueness/secrecy assertions, reran successfully.
- First171-asset unit run:703 passed/one failed; HCMUS composite 5,105,133 bytes exceeded the standalone3,000,000-byte limit. Applied the existing15,000,000-byte composite bound to both named composites rather than disabling size checks. Full705-test rerun passed. Build was not run in the failed `&&` chain; it did run successfully afterward.
- Earlier browser race answered before the workspace was rendered; now waits for the actual matched workspace title, never silently skips unmatched IDs.
- **Remaining editor-only diagnostics:** final active LSP checked8 paths;4 confirmed clean,3 inconclusive, Python authoring script has five Pyright unresolved imports (numpy, onnxruntime, soundfile, kokoro_onnx, local english_audio_build). External pinned authoring venv/scripts are not resolved by the workspace interpreter. Runtime generation/cached rerun passed in that venv. Findings deferred with explanation; no inline suppression or production dependency additions, no blanket LSP-clean claim.
- Existing Gradle wrapper/deprecation/OpenJDK-sharing and Node experimental-localStorage warnings remain. Python negative test intentionally reports a missing temporary MP3 while verifying previous-output preservation; all six tests pass.

## Release boundaries

This is a complete **local implementation/verification checkpoint**, not a production release or a calibrated institutional mock. Browser API fixtures/fake mic are not real authenticated JWT/cookie/DB/Redis/CORS acceptance; owner-change/storage-event simulations are not server logout proof. Physical mobile and human pronunciation/pedagogy still require review. AI remains disabled; broader beta/AI/privacy/purge/snapshot gates unchanged. No full third-party rights clearance, audio server storage, auto-grading, device sync or certificate claims. Existing UtilityBubble overlap is a shared-shell follow-up, not silently refactored here.
