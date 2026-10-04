#!/usr/bin/env bash
# Default: Testcontainers. --local-postgres: disposable loopback cluster, never an app DB.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${1:-}" == "--local-postgres" ]]; then
  for binary in initdb pg_ctl createdb python3; do
    command -v "$binary" >/dev/null || { echo "Missing test tool: $binary" >&2; exit 1; }
  done
  test_root=$(mktemp -d "${TMPDIR:-/tmp}/kg-admin-test.XXXXXX")
  cleanup() {
    pg_ctl -D "$test_root/data" -m immediate -w stop > /dev/null 2>&1 || true
    # Keep logs/data for diagnostics; no arbitrary recursive deletion.
    echo "Disposable PostgreSQL stopped. Test evidence directory: $test_root"
  }
  trap cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  initdb -D "$test_root/data" -A trust -U kg_test > "$test_root/init.log"
  port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
  pg_ctl -D "$test_root/data" -l "$test_root/postgres.log" -o "-h 127.0.0.1 -p $port -k $test_root" -w start
  createdb -h 127.0.0.1 -p "$port" -U kg_test kg_test_admin
  export KG_TEST_JDBC_URL="jdbc:postgresql://127.0.0.1:$port/kg_test_admin"
  export KG_TEST_DB_USER=kg_test KG_TEST_DB_PASSWORD=''
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--local-postgres]" >&2; exit 1
fi
./gradlew ${JAVA_HOME:+-Dorg.gradle.java.installations.paths="$JAVA_HOME"} \
  :kg-core:test \
  :kg-infrastructure:test --tests '*AdminUsersPersistenceTest' --tests '*FlywayDatabaseMigrationTest' \
  --tests '*JwtAuthenticationFilterTest' --tests '*BlockedOAuthAccountTest' \
  --tests '*AdminModerationPersistenceTest' --tests '*AdminLearningPersistenceTest' --tests '*LearningDraftPersistenceTest' \
  --tests '*QuestionVisibilityQueryTest' --tests '*QuestionDeleteSafetyTest' --tests '*ModulePublicationCountsTest' \
  :kg-presentation:test --tests '*AdminUsersHttpTest' --rerun-tasks --console=plain
