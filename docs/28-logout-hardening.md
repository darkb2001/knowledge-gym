# Logout hardening — 2026-10-04

## Contract

`POST /auth/logout` remains 204 on success and clears the refresh cookie. Actor is taken from the verified JWT principal, never from request body.

- Valid refresh cookie: revoke that family and invalidate already-issued access tokens for its user.
- Missing/unresolvable refresh cookie + verified principal: invalidate the principal's already-issued access tokens. Legacy access JWTs have no family id; do not guess/revoke every refresh family.
- No cookie and no principal: idempotent no-op with cookie clearing.
- Cookie user and authenticated principal disagree: 400 before mutations.
- Invalid/expired refresh JWT may resolve through its audit hash. Token parsing fallback must not catch database/cache mutation failures. The controller's call is transactional; an infrastructure failure must not be reported as a successful logout.
- Other devices' refresh families remain usable. The user-level cutoff can reject their old access tokens, after which they can refresh normally. This is not logout-all-devices.

## Browser behavior

`lib/auth.ts` records logout intent **before** making the request and clears local session/user data. It sends the captured bearer plus cookie without automatic refresh. A 401 from a stale bearer retries cookie-only logout once; it never refreshes during logout.

`lib/api-client.ts` blocks session restore, protected requests and uploads while logout intent exists. Intent is a non-secret localStorage flag, with a first-party cookie fallback. It survives reload and is observed by other tabs at auth/request boundaries. It does not store JWTs or grant/revoke backend permissions.

Only explicit successful login/registration/OAuth session installation clears intent. Failed login does not clear it. A session generation counter prevents old refresh/login/protected responses from installing session/user data after logout or overwriting a subsequent login. Stale refresh promises are detached and cannot clear newer in-flight state.

Ordinary refresh network/server errors still preserve a session when the user has **not** requested logout.

## Limits — do not promise impossible server guarantees

- Offline/failed logout can leave a server refresh family valid until server logout/revocation/expiry. Browser intent prevents normal app auto-login, but cannot remotely revoke a token when no request reaches BE.
- The fence is client UX state, not a security boundary against stolen tokens or users manually modifying browser storage.
- If both persistent browser storage mechanisms are unavailable, the in-memory fence survives only until reload. No claim of persistent protection in that environment.
- Requests already accepted by the server cannot be undone. Existing rendered/cached content in another tab is not erased by a request-boundary fence.
- Cross-tab login/logout concurrency and live cookie delivery still need browser/full-stack evidence. No production mutation or deployment was performed for this patch.

## Verification

- Backend: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home bash scripts/test-admin-platform.sh --local-postgres` — BUILD SUCCESSFUL; 228 core, 50 selected infrastructure, 10 MVC tests; zero failures/errors/skips.
- Core logout tests cover verified-principal fallback, missing/invalid cookie, anonymous idempotence, identity mismatch and mutation error propagation, alongside existing family revocation tests.
- Added `AuthIntegrationTest.logoutWithoutCookieInvalidatesBearerAndLeavesOtherRefreshFamilyUsable`; compiles, but full Docker-dependent integration suite was not executed locally.
- Frontend: `npm run build && npm run typecheck && npm run lint && npm test` passed. Final suite: 134 tests / 22 files.
- New logout tests cover offline/reload persistence, explicit vs failed login, stale refresh, stale bearer cookie-only retry, other-tab intent, delayed login/private responses, and cookie fallback when localStorage throws.
- Type/lint checks and diff checks pass. LSP has no type errors; auxiliary project heuristics report Next/bundler extensionless imports, environment guards and Vietnamese spelling hints, not ESLint/typecheck failures.
- Evidence: `/tmp/kg-logout-be-tests.log`, `/tmp/kg-logout-fe-final-tests.log`.

Run full CI on a Docker-enabled runner and live browser cookie/redirect checks before claiming production verification.
