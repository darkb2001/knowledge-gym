# Redesign verification and handoff

## Current scope

The owner delegated visual choices, permitted code-led implementation and requested investigation/review plus fixes. Study Commons covers auth, topic discovery, questions/answers, flashcards, quiz, interview, notes, dashboard, mindmap, blog and Writer/Search Admin. The newer Profile and Elasticsearch lifecycle functionality is retained.

The source at the latest investigation differed from the earlier tested redesign: tracked pages had reverted to the old identity, while new redesign files remained. HEAD is now `9bb83e2` rather than the earlier checkpoint's `a7732bfdc24bf2d0a014e3ebe367a2b460c8f576`. The cause of the intervening source change is not attributed. The frontend was reconstructed from `/tmp/kg-redesign-checkpoint/frontend.patch` in a temporary directory and reconciled with the current profile/ES features, not blindly reset. No unrelated backend source was changed, and no Git reset/staging/commit was performed by the assistant.

Open Design produced no artifact (authentication failure, then daemon unavailable). This is not a comp reproduction. The independent reviewer workflow failed before a reviewer launched; direct review is disclosed in `FINISH-REVIEW.md`, followed by the bounded correction verdict in `VERDICT.md`. No independent approval or craft score is claimed.

## Localhost diagnosis and repair

- The old Next process listened only on `127.0.0.1`; the IPv6 localhost address was not served.
- A restart initially failed because `.next` had no valid production build. Dependencies/source were reconciled, production rebuilt, then the owned process restarted.
- `start:local` now runs `next start --hostname :: --port 3100`. On this machine it serves both IPv4 and IPv6. Fresh browser smoke confirms HTTP **200** and the beige UI on `localhost`, `127.0.0.1`, and `[::1]`, with zero failed Next assets/page exceptions.
- Backend `http://localhost:8080/actuator/health` still fails: curl exit **7**, “Failed to connect to localhost port 8080”. The frontend can load; real session restoration/login cannot work until the backend is available. The UI now explains the outage with retry/back-to-login rather than a raw English fetch error. No auth bypass was added.

Open **http://localhost:3100/login**. To reproduce from this frontend directory:

```sh
npm run dev:local       # Development, no production build required
npm run preview:local   # Build, then serve production on port 3100
# Or, after a successful build:
npm run start:local
```

Do not run two servers on the same port. `::` binds all interfaces, not only loopback; these are development/preview helpers, not public deployment instructions. Server log: `/tmp/kg-redesign-next.log`.

## Functional repairs

- Profile avatar upload no longer calls a busy-guarded save while already busy and silently skips PATCH. Persistence is separate; upload failures clear busy, selecting the same file can retry, and successful upload stores its URL.
- Profile load failure/retry, name validation, disabled saving/uploading states and cancellation are handled. Successful name changes update stored display name and dispatch `kg:user-changed`; the shell reflects the change immediately.
- Elasticsearch status/start/stop are retained. Stop requires confirmation; cancelled stop issues no request; starting reloads configuration; unavailable ES-dependent modes remain disabled. Long lifecycle output wraps.
- Small-laptop navigation scrolls rather than losing bottom/admin links. Public mobile header exposes login. Auth connectivity error includes a back-to-login link.
- Auth form and full-page session states have main landmarks. Known browser transport failures have useful VI/EN messages. Deprecated Phosphor imports use supported Icon-suffix exports; rendering is unchanged. Review duration formatting is equivalent but no longer uses nested ternaries.
- Writer previews still pass through existing DOMPurify sanitization; `dangerouslySetInnerHTML` is not raw untrusted insertion. Browser assertions check sanitized answer and writer preview content.

## Project gates — current source/build

The owner subsequently requested fixing the 11 dependency findings. Current framework is Next 15.5.24 / React 19.3.0; Vitest is 4.1.11. Clean lockfile install and fresh production/browser gates pass; details and compatibility edits are in `SECURITY-REMEDIATION.md`.

Run from `knowledge-gym/kg-frontend`:

| Exact command | Actual output/result |
| --- | --- |
| `npm run lint` | Runs `eslint . --max-warnings=0`; no diagnostics, exit 0 |
| `npm run typecheck` | Runs `tsc --noEmit`; no diagnostics, exit 0 |
| `npm run test` | `Test Files 4 passed (4)`; `Tests 19 passed (19)`, exit 0 |
| `npm run build` | `Compiled successfully`; lint/types passed; `Generating static pages (19/19)`; optimization/traces completed, exit 0 |
| `git diff --check` | No output, exit 0 |
| `/Users/bao2k1/Documents/javaNote/.pi/skills/impeccable/scripts/impeccable detect --json app components tailwind.config.ts` | `[]`, exit 0 |
| `npm audit --json` | All severity counts and total **0**, exit 0 |
| `npm audit --omit=dev` | `found 0 vulnerabilities`, exit 0 |
| `npm ci` | Clean installation succeeds; `found 0 vulnerabilities`, exit 0 |
| `npm run test -- --coverage` | 19/19 pass, V8 provider works; measured line coverage 60.93%, exit 0 |

Vitest's config is now native ESM (`vitest.config.mts`); the previous CJS Vite warning is gone. npm still emits non-blocking legacy-tool deprecation/install-script-policy notices, recorded in the security handoff. Tests cover API/session behavior, topic filtering/accent-insensitive search, localization/known transport errors, review intervals, translation-key coverage and text/control contrast.

Fresh active LSP probe at warning/error severity checked nine central changed files: eight confirmed clean, one inconclusive (push-only/silent-on-clean behavior), zero warning/error diagnostics. This is not a whole-workspace clean claim. Vietnamese typos suggestions are English-dictionary false positives; two Record-widening hints are non-blocking. The deprecated Phosphor hints and nested-ternary findings were addressed. A separate writer/flashcard probe confirmed writer clean and reported a generic Semgrep flashcard HTML warning. `sanitizeAnswerHtml` was inspected: it calls DOMPurify with explicit tag/attribute allowlists and URI policy before insertion. That warning was marked a session false positive, not hidden with an inline suppression; the sanitizer remains intact. The session cache still displays old missing-export findings as stale and presence-only HTML findings even after false-positive dispositions were recorded for the sanitized flashcard and all three sanitized writer previews. The current source, completed compiler gates and fresh active probe—not an empty cache—are the evidence; no scanner rule or sanitizer was removed to make those cached counts disappear.

## Browser verification — recaptured current build

Temporary QA dependencies live outside the repository:

```sh
npm install --prefix /tmp/kg-browser-check playwright @axe-core/playwright lighthouse
KG_UI_URL=http://localhost:3100 NODE_PATH=/tmp/kg-browser-check/node_modules node scripts/verify-redesign.mjs
KG_EXPECT_BACKEND_OFFLINE=1 NODE_PATH=/tmp/kg-browser-check/node_modules node scripts/verify-localhost.mjs
```

The scripts use installed Google Chrome on macOS; set `KG_CHROME_PATH` for another installed browser. On another platform use installed Playwright Chromium. Do not use `KG_QA_RESUME=1` when checking changed source/build. `KG_EXPECT_BACKEND_OFFLINE=1` asserts the known outage UI; omit it when the backend is available.

`browser-verification.json` records the fixture pass:

- **39 explicit interaction checks** on desktop/mobile.
- **44 full-page screenshots** and axe WCAG 2/2.1 A/AA scans; **0 violations** in captured states.
- **0 uncaught page exceptions**, **0 unmatched fixture routes**, **0 horizontal document overflow**.
- Desktop (1440×1000), mobile (390×844), small laptop (1280×720) and narrow mobile (320px) included.
- Original auth/password payloads, topic-first destinations, VI search/EN persistence, module deep links, sanitized reading, note save, SRS keys/quality 0–3, quiz/interview submissions, versioned search saves/error/retry, ES confirmation/lifecycle, writer publishing, profile PATCH/upload failure/retry, navigation roles and logout/denied refresh checked.

All API calls and writes in this script are intercepted. Accounts, profile pictures, study statistics, credentials, drafts and learning samples are **synthetic QA fixtures**, not production content or live integration evidence. No real user/article/note/review/ES process was changed by QA.

`runtime-verification.json` records separate **unintercepted** checks: all three localhost hostnames serve login successfully; four desktop/mobile login/backend-outage captures have zero WCAG axe violations, page exceptions or failed Next resources. This proves frontend serving and real outage handling, not backend availability.

Screenshots are gitignored under `.impeccable/review/`. Durable reports are in `docs/design/`. The first inspection and the post-fix confirmation are recorded in `FINISH-REVIEW.md` and `VERDICT.md`; no further cosmetic hunt was performed.

### Real login Lighthouse

```sh
CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' /tmp/kg-browser-check/node_modules/.bin/lighthouse http://localhost:3100/login --chrome-flags='--headless --no-sandbox' --only-categories=performance,accessibility,best-practices --output=json --output-path=/tmp/kg-lighthouse-security.json --quiet
```

Exit 0; fresh Next 15 summary in `lighthouse-login.json`: **performance 99, accessibility 100, best practices 100**. LCP **2.3s**, FCP **0.8s**, CLS **0**, TBT **10ms**, main landmark score **1**. Previous pre-remediation sample was performance 95/LCP 2.9s; accessibility was 98 before the landmark fix. The new lab sample is below the 2.5s LCP goal. This is real login only; field INP and application-wide performance are not measured.

## Failed attempts, corrected checks and limits

- Initial production restart lacked a valid build; rebuilding corrected it. Historical build evidence was not reused as proof of the recovered tree.
- Runtime QA initially used a broad alert locator that also matched Next's route announcer; it failed with a strict-mode violation. The assertion now targets the explicit visible recovery message, and the full fresh rerun exits 0.
- Two standalone CommonJS SVG-extraction commands (`node -e` requiring the package and rendering CompassIcon, then Compass) failed with React's “Element type is invalid” because the package's CommonJS entry exposed no keys in Node 26. Package export metadata was inspected; the documented ESM import succeeds. This was a documentation helper, not a frontend failure; Next build and fresh real rendering both pass. No dependency files were modified to mask it.
- Earlier historical fixture locators and contact-sheet font lookup failed and were corrected; no failed capture is used as approval evidence.
- Live OAuth/cookies, backend authorization, SRS scheduling, actual storage/upload, Elasticsearch failover/lifecycle and writer jobs remain **unverified**. Backend port 8080 must be started/configured by the owner before real login can work.
- Unknown backend messages, course content, names/tags and technical identifiers are not automatically translated. UI locale does not imply English course availability.
- Automated axe/Lighthouse success is not WCAG certification; manual assistive-technology checks remain useful.
- The earlier eleven npm findings have been remediated at the owner's request: both full and production audits now report zero. No `npm audit fix --force` was applied. See SECURITY-REMEDIATION.md for exact versions, overrides, failed lint attempt and completed checks; no blanket application-security guarantee is implied.

## Durable design system

`DESIGN.md` extracts the actual tokens, typography, layout, components and guardrails in the official eight-section format. `.impeccable/design.json` extends it with motion/breakpoints, preview-only tonal ramps, six self-contained primitive previews and identical narrative. No shipping raster assets were generated; original user/backend images are not reattributed as designer assets.
