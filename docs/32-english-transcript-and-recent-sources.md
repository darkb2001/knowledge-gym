# English Studio — opt-in transcripts and recent HCMUS/UIT sources

Pre-commit local verification checkpoint, checked 2026-10-10. **Release status at this checkpoint: uncommitted/undeployed.** it extends the preceding 39-entry release (backend `4936085`, frontend `6013889`), whose CI/deploy succeeded. It does not claim that production currently contains these changes. The preceding frontend production smoke was blocked/skipped by HTTP429.

## Source and product boundaries

- HCMUS remains the primary preparation target. Only recent official relevance (2024–2026) informs the guide; decade-old samples are excluded from preparation guidance and forecasting.
- HCMUS 2024/2025 course notices say to collect learning material from the postgraduate office at course start. A course announcement is not a publicly obtained bank of actual questions.
- UIT's 2025 first-round exam notice places its English examination at HCMUS. UIT's 2026 second-round preparation notice links a DOCX explicitly titled the HCMUS entrance examination format. Its 10/10, 15/10/5, 10/5/5 counts and introduction/guided conversation match HCMUS Appendix 5/2026. Do not generalise this to all UIT rounds or infer an IT-specific English topic bank.
- The latest checked Writing format lists completions, cloze and transformations, not email/essay. Email remains VSTEP practice. No verified recent actual-paper/topic bank establishes a forecast through the official sources reviewed; internal course material may exist.
- Canonical links and extracted-document hash: frontend `docs/english/KHTN.md`. University text/audio/papers are not imported into the exercise bank. Public availability does not establish reuse rights.

## Implemented

- **47 original entries = 31 VSTEP + 16 HCMUS**, with **18 worked responses** after owned-attempt submission only. Composite entries reuse constituent material, not 47 independent papers.
- Eight new HCMUS IDs: `hcmus-grammar-study-v1`, `hcmus-grammar-community-v1`, `hcmus-cloze-garden-v1`, `hcmus-cloze-repair-v1`, `hcmus-vocabulary-context-v1`, `hcmus-reading-wetland-v1`, `hcmus-transformations-practice-v1`, `hcmus-speaking-routine-v1`. All original practice, not recalled examination questions or predicted themes. Existing released IDs/content are preserved.
- Authenticated `GET /english/exercises/{id}/transcript?partId=...`: Listening only; optional section must belong to that exercise. DTO contains exercise ID, part ID and script text only, not keys/explanations/models. Catalog and draft DTOs still omit scripts; answer/model feedback retains submitted-owner gating.
- Show/hide beside the player before or after submit; initially hidden/no request, cached only in the current mounted section, explicit loading/error/retry, cancellation guard. Switching section/exercise resets visibility and prevents stale script display. Plain text, bilingual assisted-listening guidance, no automatic speech synthesis or audio/answer changes. Removed duplicate post-submit transcript disclosure.
- Vocabulary's learner-facing external-site reference paragraph removed in both locales at the user's request. Internal provenance/licensing records remain intact.
- Audio remains **171 MP3s**; no new Listening recordings. Verified-cache generation rebound the compiled catalog hash without re-synthesising existing clips.

## Exact verification

Backend:
```sh
cd knowledge-gym
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  bash scripts/test-english-room.sh --local-postgres
```
Output: `BUILD SUCCESSFUL in 16s`; `17 actionable tasks: 17 executed`; disposable PostgreSQL stopped. XML totals: **285 core + 17 infrastructure + 14 presentation = 316**, zero failures/errors/skips. Includes expanded objective submission, all 18 models and authenticated transcript/section/error boundaries. Log `/tmp/kg-transcript-be.log`. Existing wrapper `./gradlew: line 49: : command not found`, deprecated-API and JVM-sharing warnings remain; the task itself completed successfully.

Frontend:
```sh
cd knowledge-gym-frontend
node scripts/sync-english-test-fixture.mjs
KG_KOKORO_MODELS=/tmp/kg-kokoro-model KG_KOKORO_PYTHON=/tmp/kg-kokoro-env/bin/python \
  node scripts/generate-english-audio.mjs
npm run lint && npm run typecheck && npm test && npm audit --omit=dev && npm run build
python3 scripts/test-english-audio-build.py
```
Output: `Synced 47 authored test entries`; audio generation exit0; ESLint `--max-warnings=0` and TypeScript no-emit exit0; **38 files / 708 tests passed**; `found 0 vulnerabilities`; Next production build exit0; Python `Ran 6 tests ... OK`. The helper's intentional missing-temporary-MP3 diagnostic tests preservation of prior output. Logs `/tmp/kg-transcript-{audio,fe-tests,fe-build,python}.log`.

Built local server and fixture browser:
```sh
NODE_PATH=/tmp/kg-english-browser/node_modules KG_UI_URL=http://127.0.0.1:3226 \
  KG_UI_CAPTURE=0 \
  KG_UI_CAPTURE_TARGETS=hcmus-guide,hcmus-listening,hcmus-grammar,listening-transcript,listening-feedback,vocabulary-learn,vocabulary-write,vocabulary-listen,vocabulary-quiz,vocabulary-match \
  node scripts/verify-english-room.mjs
```
Output: **271 checks / 51 screenshots**, `errors: []`, `accessibilityViolations: []`, unmatched API requests0. Three viewport classes: 1440×1000, 390×844, 844×390. Transcript hidden/no fetch, error/retry, show before submit, hide/cache, correct group script, section reset and deliberately delayed previous-section response covered; audio/answers continue working. All six added objective tasks submit correct raw counts on all viewport classes; all 18 models tested on desktop. Vocabulary no longer displays the external-reference link. Refreshed affected captures only; unchanged captures retained from the prior full batch. Summary in frontend `docs/english/browser-verification.json`; log `/tmp/kg-transcript-browser.log`.

Impeccable:
```sh
/Users/bao2k1/Documents/javaNote/.pi/skills/impeccable/scripts/impeccable detect --json \
  components/english/EnglishRoom.tsx components/english/VocabularyStudio.tsx \
  components/english/ListeningTranscript.tsx components/english/ExamPreparationGuide.tsx
```
Output `[]`, exit0. Initial wrong relative skill path exited127; corrected to the installed absolute path, not bypassed.

### Corrected failure and remaining diagnostics

- Initial backend run:285 core tests/one failure because the new transformation model did not meet worked-response coverage size. Added meaningful explanation of preserved time/causality; complete rerun316 passed.
- Active LSP checked five changed UI/API files: two clean, two existing `beforeunload.returnValue` deprecation hints deferred pending cross-browser compatibility acceptance; existing client-only callback serialization warning marked false-positive (no Server Component boundary, no inline suppression). Nineteen auxiliary spelling suggestions on Vietnamese text are informational, not build errors. No blanket LSP-clean claim. Earlier external audio-authoring Pyright import-resolution gaps remain as documented in doc31.

## Remaining acceptance

Browser APIs are intercepted local fixtures, not live authenticated FE/BE/DB/Redis/CORS acceptance. Synthetic microphone/device tests and codec/hash success are not physical iOS/Android or human pronunciation/pedagogy review. No calibrated mock, certificate grade, subjective auto-marking or guaranteed forecast. The new transcript exception intentionally permits assisted listening; raw correct counts never establish an unaided result or official score. AI remains disabled; broader beta/security/privacy release gates are unchanged. No commit/push/deploy had been performed at this pre-commit checkpoint. Subsequent release SHAs and CI/deploy outcomes must be checked in Git history/GitHub Actions rather than inferred from local verification.
