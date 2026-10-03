# Admin platform and autonomous knowledge intake

## Approved scope

The owner approved full website administration plus an AI knowledge-intake system, not just content entry forms. Implement incrementally; existing uncommitted backend/frontend work must be preserved. Do not deploy or enable frontend capabilities before verification.

1. Account directory, suspension, roles, session revocation and audit; protect the last active administrator.
2. Comment moderation: directory, hide/delete/restore with actor and reason.
3. Blog lifecycle: draft/published/hidden/archived/deleted, restore, revision history and consistent public/search visibility.
4. Learning administration: content publishing lifecycle, quiz/flashcard/interview source content, and user learning-data inspection/reset with audit and historical integrity.
5. Real frontend admin surfaces and API wiring for each group, including RBAC and error states.
6. Autonomous knowledge pipeline: configurable goals/sources/schedules/budgets; discovery, collection, provenance, deduplication, quality checks, draft/review or policy-based publication, revisions and rollback. Reuse existing collector/writer foundations, but do not claim they already implement this pipeline.

## Existing learning model

Quiz, SRS flashcards and TEXT mock interviews currently reuse the topic/module/question bank. Users start quiz/interview sessions or enroll SRS cards; they do not populate the shared knowledge bank. Fixed authored quiz sets, independent flashcard decks and curated interview scenarios are additional modeling work, not existing capabilities. Future interview work is deferred: mock interviews record answers and show the authored sample answer; there is no keyword or AI scoring (V035 removed it), and an AI interviewer does not exist yet.

## Initial checkpoint: account administration backend (historical)

The evidence and pending list in this initial checkpoint are superseded by the verified follow-up below.

### Implemented locally

- `GET /admin/users?q=&role=&blocked=&page=1&size=20`
- `GET /admin/users/{id}`
- `PATCH /admin/users/{id}/role`: `{ "role": "USER|PREMIUM|ADMIN", "reason": "..." }`
- `PATCH /admin/users/{id}/status`: `{ "blocked": true, "reason": "..." }`
- `POST /admin/users/{id}/revoke-sessions`: `{ "reason": "..." }`, returns 204.

All controller actions require ADMIN. Mutations also recheck the actor against current database state inside a transaction. PostgreSQL transaction advisory lock serializes these security mutations, protecting the last-active-admin invariant from concurrent removals. Self-block and self-role-change are rejected. Reasons are required, maximum 500 characters. No password hashes, refresh tokens or OAuth identifiers are returned.

Role/status mutations revoke outstanding PostgreSQL refresh tokens and invalidate earlier access JWTs. Redis cache entries alone cannot authorize refresh rotation: the existing PostgreSQL compare-and-set rejects revoked refresh tokens. Access requests check database role, suspension and invalidation timestamp, not stale role claims. Local login, refresh and OAuth reject suspended accounts.

Migration `V027__admin_user_security.sql` adds `blocked`, `tokens_invalid_before` and a directory index. V025 and V026 already exist in the working tree and must be included in migration verification. No existing migration was rewritten.

Access JWT `iat` has second resolution: a token issued within the same second as invalidation may also be rejected. Sign in again in the next second. Requests already executing before suspension are not retroactively cancelled. Database state is checked on every access request (additional DB read); do not introduce caching without a reliable invalidation design. Production requires V027 before deploying the new filter.

### Verification evidence

Active LSP diagnostics on the new use case, JDBC adapters, JWT filter and controller: 5 files checked, zero diagnostics.

Initial Gradle invocation failed: JDK 21 toolchain not detected. Corrected using installed JDK:

```sh
cd knowledge-gym
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
:kg-core:test :kg-infrastructure:compileTestJava :kg-presentation:compileTestJava --console=plain
```

Result: `BUILD SUCCESSFUL`; 198 core tests, zero failures/errors, including 8 new management-policy tests. Infrastructure and presentation test sources compile. Wrapper emits pre-existing `./gradlew: line 49: : command not found`; compilation also reports deprecated API warnings. Neither was changed in this scope.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
:kg-infrastructure:test --tests '*AdminUsersPersistenceTest' \
--tests '*FlywayDatabaseMigrationTest' --console=plain
```

Result: timed out after 120 seconds at `:kg-infrastructure:test`; Docker info probe also timed out. No passing integration result is claimed. Persistence tests were added for suspension/revocation, audit, rollback, role authority, literal search and pagination. Migration assertions now expect all 27 migrations and the new columns.

`git diff --check`: passed. No commit/deploy performed.

### Remaining before account-management release

- Restore a responsive Docker/Testcontainers environment and run migration/persistence tests.
- Add/run HTTP RBAC integration tests: anonymous 401, USER/PREMIUM 403, ADMIN actions; test stale JWT, local login, refresh and OAuth under suspension.
- Exercise concurrent last-admin mutations against PostgreSQL.
- Add admin user frontend; it does not exist yet.
- Decide separate policies for account creation/invitations, profile edits and deletion/anonymization; these are NOT implemented by this checkpoint.
- Verify the full auth regression suite and deployment ordering.

## Verified follow-up: user security, moderation, blog lifecycle and learning inspection

### Delivered locally

- Frontend `/admin/users`: searchable/filterable user directory, role changes, suspension/unblock, session revocation, required reasons and confirmation.
- Frontend `/admin/comments`: searchable/filterable comments, hide/show, soft-delete and restore to HIDDEN. Restoration requires a separate visibility approval.
- Frontend `/admin/posts`: HIDDEN/DELETED filters and hide/archive/soft-delete/restore controls with required reasons. Restored articles enter REVIEW, never silently become public. Existing publish API is reused.
- Manual blog DRAFTs can now be edited through the writer endpoint with sanitation and revision storage. Public/removed posts cannot be resurrected by an edit; hide/restore first.
- Frontend `/admin/learning`: per-user paginated QUIZ/SRS/INTERVIEW/PROGRESS inspection, plus audited SRS schedule reset. Reset preserves attempts, XP, mastery and quiz/interview history.
- Navigation exposes the new admin routes only to admin accounts. Every backend controller independently enforces ADMIN.

New endpoints:

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/admin/blog/comments` | q/postId/userId/status/page/size filters |
| PATCH | `/admin/blog/comments/{id}/status` | VISIBLE/HIDDEN/DELETED plus reason |
| DELETE | `/admin/blog/comments/{id}` | Soft deletion, reason body |
| POST | `/admin/blog/comments/{id}/restore` | Restores DELETED to HIDDEN, reason body |
| PATCH | `/admin/blog/posts/{id}/status` | HIDE/ARCHIVE/DELETE/RESTORE plus reason |
| DELETE | `/admin/blog/posts/{id}` | Soft deletion, reason body |
| POST | `/admin/blog/posts/{id}/restore` | Restores removed post to REVIEW, reason body |
| GET | `/admin/users/{userId}/learning` | kind=QUIZ/SRS/INTERVIEW/PROGRESS, page/size |
| POST | `/admin/users/{userId}/learning/srs/{cardId}/reset` | Schedule-only reset, reason body |

`V028__admin_blog_moderation.sql` extends blog states and adds comment moderation status. Public comment reads omit removed/hidden content. Replies to removed/hidden comments are rejected. Post mutations fire existing search-outbox triggers; search relay must be running for Elasticsearch eventual consistency. Physical deletion is intentionally not exposed.

### Reproducible tests and results

```sh
cd knowledge-gym
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
bash scripts/test-admin-platform.sh --local-postgres
```

Result: BUILD SUCCESSFUL, all 17 tasks executed (runner uses --rerun-tasks). Core: 200 tests; selected infrastructure suites: 33 tests; isolated HTTP security/MVC suite: 7 tests. Zero failures/errors. Coverage includes local login/refresh suspension, OAuth suspension, stale JWT authority, 401/403, required reasons, last-admin concurrency, migration through V028, SQL persistence, audit rollback, comment visibility, blog removal/restore, draft editing, per-user learning reads and ownership-bound SRS reset.

Local mode uses an ephemeral loopback PostgreSQL cluster, and each suite provisions a separate `kg_test_*` database. All test databases are dropped and the runner stops its cluster on exit. Existing app databases are never used. Omitting --local-postgres keeps Testcontainers as the default. Local evidence is PostgreSQL 14; PostgreSQL 16 Testcontainers/CI still needs a responsive Docker environment.

Initial local shared-DB run had five migration-suite failures due to fixture contamination and schema-unscoped catalog assertions. Fixed isolation and schema scoping; the forced rerun passes. The pre-existing Gradle wrapper warning at line 49 and deprecated-API warnings remain.

```sh
cd knowledge-gym-frontend
npm run typecheck && npm run lint && npm test
npm run build
NODE_PATH=/tmp/kg-browser-check/node_modules KG_UI_URL=http://localhost:3211 \
node scripts/verify-admin-platform.mjs
```

Frontend: typecheck/lint pass; 78 tests across 13 files pass; production build succeeds. Browser fixture tests: 20 checks, eight desktop/mobile captures, zero browser errors or WCAG A/AA violations. Screenshots/report are under `.impeccable/review/admin-platform/`. These are intercepted synthetic API fixtures, not live full-stack persistence evidence. Manual detector returned no findings. LSP: five confirmed clean, four TypeScript checks inconclusive; TypeScript compiler passed independently.

### Verified follow-up: autonomous knowledge-intake control plane

### Delivered locally

- `V029__knowledge_intake_goals.sql` adds goals, queued intake runs and audit records.
- `GET/POST /admin/knowledge/goals` lists and creates goals.
- `GET /admin/knowledge/goals/{id}` reads a goal.
- `PATCH /admin/knowledge/goals/{id}/status` activates, pauses or archives a goal with a reason.
- `POST /admin/knowledge/goals/{id}/runs` queues a manual run only for an ACTIVE goal.
- `GET /admin/knowledge/goals/{id}/runs` lists paginated run state.
- `/admin/knowledge` provides bilingual goal/source/budget controls and explicit run activation.
- The worker is disabled by default and, when enabled, claims one queued run, restricts collection to the goal's HTTPS domain allowlist, queues one idempotent writer job when items are found, records REVIEW state and never publishes or mutates users, scores or roles.
- Localhost, loopback/private-network source domains, HTTP sources, credentials and non-default ports are rejected. Domain allowlists are normalized before storage. Item/cost limits and reason lengths are bounded.

This is the first safe automation layer, not a claim that AI can freely browse the internet. It collects source material into review. Existing writer generation remains a separate step; `autoPublish` is stored as policy but is not silently honored by this worker. A future publisher must enforce source provenance, content sanitation, duplicate detection, quality gates, budget checks and rollback before using that policy.

### Verification

The local PostgreSQL runner now passes migration V031 with 48 application tables, approved materialization and duplicate-content registry, all selected security/moderation/learning tests, and the new goal validation tests. Frontend typecheck, lint, production build and Vitest pass with 13 files / 79 tests. LSP reports no errors on the new intake, learning-draft, materialization and duplicate-detection paths. No production worker flag was enabled.

## Still pending — not implied by these passing tests

- Full application auth/Redis integration regression and live full-stack/production checks.
- Account creation/invitations, profile edits, deletion/anonymization policies.
- Detailed answer inspection and audited correction/removal/reset of quiz/interview histories or progress; current learning controls are inspection and SRS schedule reset only.
- Content lifecycle/historical snapshots for question updates/removals, and optional independently authored quiz/deck/interview collections.
- Autonomous knowledge discovery/ingestion/publication, service identity, source-management UI and goal/budget configuration. Existing blog collector/writer is NOT a completed autonomous learning-content pipeline.

Agent identity must have content-only service permissions, never user-role or learner-score mutation privileges. Treat fetched text as untrusted data; require URL/network restrictions (including SSRF protection), source provenance, content sanitation, bounded retries/costs, idempotency and rollback. Automatic publication is an explicit owner policy, not a default inferred from an AI quality score. No production deployment or capability switches were enabled.
