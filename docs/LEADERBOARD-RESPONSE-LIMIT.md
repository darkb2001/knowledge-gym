# Dashboard leaderboard response limit

`GET /dashboard/leaderboard` now returns at most ten ranked learners by default. An optional integer `limit` requests a smaller subset; numeric values clamp to **1–10**. Examples: no parameter → up to ten, `limit=5` → up to five, `limit=100` → up to ten, `limit=-1` → up to one. Empty source results stay empty. Non-integer parameters retain Spring's existing binding behavior.

Response row shape and authorization are unchanged. Ordering/ranks still come from `QueryLeaderboardUseCase`; no new statistics or renumbering are introduced. The existing Redis top-100 shared cache and bounded rebuild remain intact; HTTP clients no longer need to receive its whole contents. Consumers requiring a larger leaderboard must use a separately designed paginated contract, not rely on the previous 100-row dashboard response.

The frontend explicitly sends `limit=10`, displays five initially, and lets the user expand to ten. A legacy backend that ignores the query parameter still works because the frontend also caps retained rows, but **the backend must be deployed** to reduce HTTP payload.

## Verification

Two standalone MockMvc tests in `DashboardLeaderboardLimitTest` passed: default top-ten ordering, smaller requests and oversized/negative bounds. They exercise the HTTP parameter/default contract, not authentication or persistence.

Command on this macOS host:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  :kg-presentation:test --tests '*DashboardLeaderboardLimitTest' --console=plain
```

Result: `BUILD SUCCESSFUL`; two tests, zero failures. Initial toolchain autodetection failed until the installed Homebrew Java 21 path was supplied. The existing Gradle wrapper warning at line 49 remains unrelated. Docker readiness timed out, so Testcontainers-backed dashboard integration tests were not executed in this pass.
