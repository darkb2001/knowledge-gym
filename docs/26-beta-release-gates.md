# Beta release gates — implementation checkpoint

Status: **NOT approved for public beta deployment**. This document supersedes earlier conversational claims that passing the existing test suites proved the new AI paths complete. Earlier sections describe their historical checkpoints, not the current deployment state. Current operational evidence and the end-user contract review are recorded in [27-end-user-backend-review.md](27-end-user-backend-review.md); successful CI/deployment does not grant beta approval.

## Verified in this checkpoint

- Fixed learning-draft INSERT: nine columns now have nine bound parameters. Existing suites had not exercised this path.
- Review is a conditional atomic update; a repeated/concurrent decision returns conflict without a false audit entry.
- Materialization uses one JDBC transaction, locks the draft and target module, validates typed payloads, and writes content/options/registry/audit together. No dependency on pending JPA flushes before FK inserts.
- Duplicate identity uses canonical typed JSON and SHA-256. It detects exact normalized content within a module, NOT semantic equivalence or duplicates of legacy questions without a registry entry.
- Invalid question options, empty sanitized answers and unknown difficulty are rejected before writes.
- Materialized content remains DRAFT. No user enrollment/session/history is created.
- Rollback endpoint `POST /admin/knowledge/drafts/{id}/rollback` changes content to HIDDEN, retaining question IDs, options, SRS cards, attempts and registry. It is withdrawal, not restoration of an earlier revision.
- Question lifecycle changes record authenticated actor, previous/new status and reason in audit_logs atomically. Status changes no longer invoke option generation.
- Elasticsearch projection excludes non-PUBLISHED questions. V033 captures visibility changes in search_outbox. Search updates remain eventually consistent.
- Admin draft workspace has status filters, pagination, named module selection, explicit confirmation, error/loading feedback, withdrawal and a same-turn submission lock. Approved drafts are accessible after reload.
- New learning AI generation fails closed by default via `app.knowledge.learning-generation.enabled=false`. Do not enable it until budget reservation, accounting and provider validation are completed; its current cost calculation is not production evidence.

## Test evidence

Commands:

```bash
cd knowledge-gym
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  bash scripts/test-admin-platform.sh --local-postgres

cd ../knowledge-gym-frontend
npm run typecheck
npm run lint
npm test
npm run build
```

Local disposable PostgreSQL suites: core 203, infrastructure 44, presentation 8 tests; zero failures/errors. Includes ten LearningDraftPersistenceTest cases for save/review, sanitize/materialize, duplicate rollback, invalid input, transaction rollback, concurrent submissions, flashcard/interview no-enrollment, withdrawal retaining history, search visibility/outbox, and generation disabled by default. HTTP test uses real MVC/method-security with mocked use cases, not a live JWT/database stack.

Frontend: typecheck/lint/build passed; 82 Vitest tests passed after adding materialization/withdrawal/pagination contracts. Mechanical UI detector reported no findings. New draft UI has NOT yet been browser-verified against a live backend.

Known environment warning remains: `./gradlew: line 49: : command not found`; Gradle returned BUILD SUCCESSFUL.

## Local FE/BE review follow-up

- Admin question list/count now uses an explicit repository admin scope, separate from public PUBLISHED-only search. List responses include contentStatus. Controller remains ADMIN-only; no client parameter can widen public search visibility.
- Question V2 editor adds reason/confirmation-gated publish and hide controls. Status response options are intentionally empty, so the UI retains existing choices rather than erasing them. Nonpublic questions do not link to the unavailable learner detail. AI materialization questionId deep links now open the selected editor; narrow screens focus/scroll to it.
- Legacy hard-delete/re-import removal now locks candidate questions and fails with 409 if they have SRS, attempts, quiz memberships, interview answers, daily assignments, notes, materialization registry or blog links. It no longer deletes dependencies to satisfy FKs. This is a removal guard, NOT a complete import/revision policy: retained natural-key upserts and concurrent imports still need review.
- Goal source selection excludes HTTP URLs and unrelated domains. This does NOT establish redirect/DNS/private-network SSRF safety or goal-specific writer provenance/budget enforcement.
- Latest local verification: core **204**, selected infrastructure **49**, MVC presentation **10** tests; zero failures/errors/skips. QuestionDeleteSafetyTest executes guard SQL on real disposable PostgreSQL using a mocked EntityManager/JDBC bridge; it does not prove the full Hibernate/controller path. QuestionVisibilityQueryTest verifies SQL construction using mocks. MVC services are mocked.
- Frontend typecheck/lint/build and **83 tests / 13 files** passed. Question V2 browser fixture: **68 checks, 20 desktop/mobile screenshots**, zero page errors, unmatched APIs or WCAG A/AA findings. All API writes were intercepted. Initial cold Next-dev navigation timed out; a loopback retry passed. A later dev/build overlap caused a fixture login timeout and a typecheck TS6053 generated-file race; restarting the preview and then stopping it before serial build/typecheck/lint/tests resolved both. The final browser rerun and serial frontend checks passed. No live full-stack assertion is made.
- Browser command: `NODE_PATH=/tmp/kg-browser-check/node_modules KG_UI_URL=http://127.0.0.1:3212 KG_ADMIN_V2=1 node scripts/verify-admin-workspace.mjs`; local preview used all three NEXT_PUBLIC_ADMIN_* switches=true, without changing committed defaults or production settings.
- Evidence: `.impeccable/review/admin/v2-report.json` in frontend; session logs `/tmp/kg-review-backend-check.log`, `/tmp/kg-review-frontend-check.log`, `/tmp/kg-review-frontend-final.log`, `/tmp/kg-review-browser.log`. Staged/unstaged diff checks passed in both repos.

## Required before public beta approval

- [ ] Verify all learner paths (existing SRS cards, daily assignments, ID-based quiz/interview flows, notes and module/topic counts) honor visibility policy without corrupting existing session/history semantics.
- [ ] Verify unpublished admin listing and publish/hide in a live full-stack run. Explicit admin query, MVC permissions and browser-fixture controls are now implemented/tested, but not live JWT/database/cache integration.
- [ ] Finish hard-delete/re-import policy: removal now refuses referenced questions without deleting history; retained upsert/revision, concurrency and module/topic cascades still need review.
- [ ] Live full-stack regression: JWT, PostgreSQL, Redis, cache eviction and search relay. HTTP security for every new mutation, including lifecycle.
- [ ] Verify search withdrawal propagation latency; stale indexed documents may survive until relay processing.
- [ ] Learning AI budget reservation/actual cost accounting/provider response limits, evidence validation, schema gates and safe failure responses. Keep generation disabled meanwhile.
- [ ] Intake SSRF, retry, goal/source binding and source provenance adversarial tests. Do not infer network safety from domain validation alone.
- [ ] Browser regression on desktop/mobile, accessibility and failure states for the changed draft UI.
- [ ] Staging migrations V027–V033 against a production-like snapshot, tested backup/restore, health/readiness, alerts and rollback runbook.
- [ ] Owner approves beta scope and deployment environment after gate evidence is collected.

Independent authored decks and interview scenarios are still absent; current beta scope would use the common question bank. If advertised as independent authored products, implement and test them before release. Account invitation/anonymization are also not implemented; do not advertise them as available.

## End-user contract review — 2026-10-04

See [27-end-user-backend-review.md](27-end-user-backend-review.md) for the product matrix, endpoint assessment, priorities and evidence boundaries. Baseline `2ec2141` has a successful GitHub deploy-prod job and public health UP; this is not a full learner-journey acceptance test.

Local follow-up fixes: published-only ID enrollment/SRS due/review/new note links/conversion; existing notes remain editable and withdrawn cards/history are retained; final quiz publication check; sample interview results only after FINISHED; learner module counts exclude nonpublic questions while removal guards retain raw counts; malformed avatar URLs and null note tags are client errors.

Local verification: **223 core + 50 selected infrastructure + 13 presentation/architecture tests**, zero failures/errors/skips; packaging `build -x test` passes. Counts are tested through real Spring Data/Hibernate on disposable PostgreSQL; note publication lookup executes actual JDBC. Updated SRS/quiz HTTP regressions compile but still require the Docker-enabled full CI run. No new migrations, production writes, flags or deployments were performed for this patch.

Full visibility gate stays open: concurrent withdrawal, inactive taxonomy, active/existing quiz/interview semantics, content/options revision snapshots, search relay latency and live HTTP integration are not proven complete. Daily challenge remains 501; PREMIUM is not a verified paid product. Do not advertise these as finished features.
