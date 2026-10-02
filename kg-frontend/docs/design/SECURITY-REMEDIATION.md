# Frontend dependency security remediation

## Result and scope

Owner requested fixing the 11 npm vulnerabilities. Full dependency audit now reports **0 vulnerabilities**, as does production-only audit. Before: 3 moderate, 5 high, 3 critical; after: all zero. Counts are affected npm packages (including transitive effects), not 11 distinct exploit paths. See `security-audit.json`.

Scope is `kg-frontend` dependencies and necessary framework/tooling migration, not a backend, container, infrastructure or whole-application security assessment. Concurrent backend edits were not touched. No `npm audit fix --force`, legacy peer-dependency bypass, audit exclusions or security rules were used to conceal findings.

## Changes

| Dependency | Before | After |
| --- | --- | --- |
| next / eslint-config-next | 14.2.35 | 15.5.24 |
| react / react-dom | 18.3.1 | 19.3.0 |
| React type packages | 18.x | 19.3.x |
| vitest / @vitest/coverage-v8 | 2.1.9 | 4.1.11 |
| vite | Vulnerable transitive 5.x | 7.3.6 |
| postcss | Vulnerable 8.x, including Next's pinned 8.4.31 | 8.5.28 throughout the resolved tree |

Next 15.5.24 is outside the reported Next advisory ranges; upgrading to Next 16 is not necessary for this audit. React 19 supports the migrated App Router and its types. Next's newer lint plugin removes the affected glob 10 dependency path. Vitest and mocker 4.1.11 remove the reported test-server file-read/execution issues; Vite 7.3.6 and its updated esbuild remove the affected development-server paths.

### Narrow, documented overrides

`overrides.postcss = "$postcss"` makes Next and the CSS tooling resolve the direct, patched PostCSS 8.5.28. Next 15.5.24 itself still specifies 8.4.31; upgrading Next alone does not remove that vulnerable transitive dependency. This is an actual same-major library replacement, not an audit filter. Production build and browser rendering pass against it.

`overrides.vite = "7.3.6"` keeps Vitest on its supported patched Vite 7 line rather than automatically taking Vite 8. Its declared dependency range allows this version. `npm ls` confirms both overrides with no invalid peer dependencies.

### Compatibility edits

- Quiz uses client `useParams` instead of synchronously reading Next 14's page params prop; module deep links and quiz payloads remain unchanged.
- Vitest config is now `vitest.config.mts`, using native ESM `import.meta.url` to resolve the alias. No CJS Vite warning remains.
- `lint` uses ESLint directly because `next lint` is deprecated. Only generated `.next`, `out`, `coverage` and `next-env.d.ts` are ignored; actual app/TypeScript lint rules remain enabled.
- Next automatically set the TypeScript target to ES2017. Its generated route type reference remains intact and still participates in typechecking.
- Supported Node range is `^20.19.0 || ^22.12.0 || >=24.0.0`, intersecting the framework/test-tool requirements. Local verification used Node 26.10.0; other supported runtimes were not separately tested.
- `package-lock.json` was regenerated and then verified by a clean `npm ci`.

## Verification — final tree

Run from `knowledge-gym/kg-frontend`:

| Exact command | Actual output/result |
| --- | --- |
| `npm ci` | 447 packages installed; `found 0 vulnerabilities`; exit 0 |
| `npm audit` | `found 0 vulnerabilities`; exit 0 |
| `npm audit --json` | All severity counts and total 0; exit 0 |
| `npm audit --omit=dev` | `found 0 vulnerabilities`; exit 0 |
| `npm run lint` | `eslint . --max-warnings=0`; no diagnostics; exit 0 |
| `npm run typecheck` | `tsc --noEmit`; no diagnostics; exit 0 |
| `npm run test` | 4 files / 19 tests passed; exit 0 |
| `npm run test -- --coverage` | 4 files / 19 tests passed; V8 provider works; 60.93% measured line coverage; exit 0 |
| `npm run build` | Next 15.5.24 compiled successfully; types/lint pass; 19/19 static pages; exit 0 |
| `git diff --check` | No output; exit 0 |
| `KG_UI_URL=http://localhost:3100 NODE_PATH=/tmp/kg-browser-check/node_modules node scripts/verify-redesign.mjs` | 39 interactions, 44 screenshots/axe scans; zero captured violations/page errors/unmatched fixture requests/overflow; exit 0 |
| `KG_EXPECT_BACKEND_OFFLINE=1 NODE_PATH=/tmp/kg-browser-check/node_modules node scripts/verify-localhost.mjs` | Real localhost/IPv4/IPv6 login HTTP 200; four real captures, zero axe violations/page exceptions/failed Next assets; exit 0 |

The owned server has been restarted on port 3100 using the new production build. Browser reports were recaptured, not resumed. API writes in fixture QA remain intercepted; no production data or real storage/ES operation was changed.

Real login Lighthouse on the upgraded build: performance **99**, accessibility **100**, best practices **100**; LCP **2.3s**, FCP **0.8s**, CLS **0**, TBT **10ms**. These are lab results for login only, not field performance or application-wide security certification.

## Failures/warnings retained honestly

- The first install emitted transient ERESOLVE warnings while replacing React 18/ReactDOM 18 with the new pair. Final `npm ls` has a consistent React/ReactDOM 19.3.0 tree, and clean `npm ci` succeeds without those peer warnings. No force flag was used.
- A lint rerun after Next generated its new route reference failed with `@typescript-eslint/triple-slash-reference` in `next-env.d.ts:3`. The generated file was excluded from lint rather than editing Next-generated code or disabling the rule for application files. Final lint/typecheck/build pass.
- One surgical config edit initially failed to match the exact existing extends array; it made no change. The actual config was reread and its existing `next/typescript` preset retained.
- `npm ci` still emits deprecation notices for ESLint 8 and some transitive tools, and this npm installation reports pending install-script approvals for esbuild/fsevents/unrs-resolver. No approvals or npm policy changes were made; install/tests/coverage/build all succeed. Deprecation notices are not reported npm vulnerabilities.
- Current active LSP probes confirmed quiz/config and writer/notes/answer consumers clean. A broader session-cache refresh remained inconclusive for TypeScript and had partial opengrep coverage due an unrelated HTML syntax error outside the frontend. Its stale missing-export entries do not match current explicitly exported UI helpers or the completed compiler result. Cached HTML presence-only warnings were inspected: Writer calls DOMPurify directly, Notes calls renderSafeMarkdown→sanitizeAnswerHtml, answers/flashcards call sanitizeAnswerHtml. False-positive dispositions were recorded without removing sanitization or adding inline ignores. No whole-workspace analyzer-clean claim is made.

## Remaining limits

Backend port 8080 is offline; real auth, OAuth, storage and backend workers remain unverified. Zero npm findings means no known matches in the registry audit at verification time, not that all possible application vulnerabilities are absent. Repeat audits as advisories evolve. The low measured coverage is disclosed; fixture UI checks are complementary, not a claim of comprehensive unit coverage.
