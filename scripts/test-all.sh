#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="python3"
MAVEN_ARGS=(-Dmaven.repo.local="$ROOT_DIR/.m2/repository")

oracle_table_exists() {
  local table_name="$1"
  local container_name="${ORACLE_CONTAINER_NAME:-review-oracle}"
  local app_user="${APP_USER:-review_app}"
  local app_password="${APP_USER_PASSWORD:-ReviewApp12345}"
  local oracle_service="${ORACLE_SERVICE:-FREEPDB1}"
  local table_count

  if ! docker ps --format '{{.Names}}' | grep -qx "$container_name"; then
    return 1
  fi
  table_count="$(docker exec -i "$container_name" bash -lc \
    "sqlplus -s ${app_user}/${app_password}@localhost/${oracle_service}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = UPPER('${table_name}');
EXIT;
SQL
)"
  [[ "$table_count" == "1" ]]
}

oracle_index_exists() {
  local index_name="$1"
  local container_name="${ORACLE_CONTAINER_NAME:-review-oracle}"
  local app_user="${APP_USER:-review_app}"
  local app_password="${APP_USER_PASSWORD:-ReviewApp12345}"
  local oracle_service="${ORACLE_SERVICE:-FREEPDB1}"
  local index_count

  if ! docker ps --format '{{.Names}}' | grep -qx "$container_name"; then
    return 1
  fi
  index_count="$(docker exec -i "$container_name" bash -lc \
    "sqlplus -s ${app_user}/${app_password}@localhost/${oracle_service}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = UPPER('${index_name}');
EXIT;
SQL
)"
  [[ "$index_count" == "1" ]]
}

oracle_constraint_mentions() {
  local constraint_name="$1"
  local expected_text="$2"
  local container_name="${ORACLE_CONTAINER_NAME:-review-oracle}"
  local app_user="${APP_USER:-review_app}"
  local app_password="${APP_USER_PASSWORD:-ReviewApp12345}"
  local oracle_service="${ORACLE_SERVICE:-FREEPDB1}"
  local constraint_count

  if ! docker ps --format '{{.Names}}' | grep -qx "$container_name"; then
    return 1
  fi
  constraint_count="$(docker exec -i "$container_name" bash -lc \
    "sqlplus -s ${app_user}/${app_password}@localhost/${oracle_service}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT COUNT(*) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = UPPER('${constraint_name}') AND SEARCH_CONDITION_VC LIKE '%' || '${expected_text}' || '%';
EXIT;
SQL
)"
  [[ "$constraint_count" == "1" ]]
}

apply_single_oracle_migration() {
  local migration_file="$1"
  local container_name="${ORACLE_CONTAINER_NAME:-review-oracle}"
  local app_user="${APP_USER:-review_app}"
  local app_password="${APP_USER_PASSWORD:-ReviewApp12345}"
  local oracle_service="${ORACLE_SERVICE:-FREEPDB1}"

  docker cp "$ROOT_DIR/database/oracle/${migration_file}" "$container_name:/tmp/${migration_file}" >/dev/null
  docker exec "$container_name" bash -lc \
    "sqlplus -s ${app_user}/${app_password}@localhost/${oracle_service} @/tmp/${migration_file}" \
    >/dev/null
}

if [ -d "/opt/homebrew/opt/openjdk/bin" ]; then
  export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
fi

if [ -x "$ROOT_DIR/.venv/bin/python" ]; then
  PYTHON_BIN="$ROOT_DIR/.venv/bin/python"
fi

if command -v mvn >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  bash "$ROOT_DIR/scripts/rabbitmq-up.sh" --optional
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    CONTAINER_NAME="${ORACLE_CONTAINER_NAME:-review-oracle}" bash "$ROOT_DIR/scripts/oracle-up.sh"
    if ! oracle_table_exists "SYS_USER"; then
      CONTAINER_NAME="${ORACLE_CONTAINER_NAME:-review-oracle}" bash "$ROOT_DIR/scripts/oracle-schema-apply.sh"
    fi
    if ! oracle_table_exists "COMMUNICATION_LOG" ||
      ! oracle_table_exists "CAMERA_READY_SUBMISSION" ||
      ! oracle_table_exists "REVIEW_DISCUSSION_MESSAGE"; then
      apply_single_oracle_migration "020_business_operations_closure.sql"
    fi
    if ! oracle_table_exists "CONFERENCE_FORM_DEFINITION" ||
      ! oracle_table_exists "CONFERENCE_FORM_FIELD" ||
      ! oracle_table_exists "REVIEW_FORM_RESPONSE" ||
      ! oracle_table_exists "AUTHOR_FEEDBACK" ||
      ! oracle_table_exists "PAPER_TAG" ||
      ! oracle_table_exists "IMPORT_BATCH" ||
      ! oracle_table_exists "PAPER_ROLE_ASSIGNMENT"; then
      apply_single_oracle_migration "021_real_platform_wave6_wave7.sql"
    fi
    if ! oracle_table_exists "REVIEWER_INVITATION" ||
      ! oracle_table_exists "EXTERNAL_REVIEWER_DELEGATION" ||
      ! oracle_table_exists "CONFLICT_RELATIONSHIP" ||
      ! oracle_table_exists "REVIEWER_MATCHING_SCORE" ||
      ! oracle_table_exists "ASSIGNMENT_PROPOSAL_BUNDLE" ||
      ! oracle_table_exists "ASSIGNMENT_PROPOSAL" ||
      ! oracle_table_exists "ASSIGNMENT_OVERRIDE_AUDIT"; then
      apply_single_oracle_migration "022_assignment_coi_maturity.sql"
    fi
    if ! oracle_table_exists "EMAIL_TEMPLATE" ||
      ! oracle_table_exists "EMAIL_TEMPLATE_VERSION" ||
      ! oracle_table_exists "OUTBOUND_EMAIL_HISTORY" ||
      ! oracle_table_exists "OFFLINE_REVIEW_IMPORT_BATCH" ||
      ! oracle_table_exists "OFFLINE_REVIEW_IMPORT_ROW" ||
      ! oracle_table_exists "CAMERA_READY_FILE" ||
      ! oracle_table_exists "PUBLICATION_METADATA" ||
      ! oracle_table_exists "PROCEEDINGS_EXPORT_BATCH"; then
      apply_single_oracle_migration "023_publication_communication_maturity.sql"
    fi
    if ! oracle_index_exists "IDX_IMPORT_BATCH_TYPE_STATUS" ||
      ! oracle_constraint_mentions "CK_IMPORT_BATCH_TYPE" "MATCHING_SCORES"; then
      apply_single_oracle_migration "024_wave8_wave9_slice_b_completion.sql"
    fi
    if ! oracle_table_exists "WORKFLOW_FORM_RESPONSE" ||
      ! oracle_table_exists "REVIEW_FORM_RESPONSE_REVISION" ||
      ! oracle_column_exists "CONFERENCE_PHASE" "REBUTTAL_CLOSE_AT" ||
      ! oracle_column_exists "CONFERENCE_PHASE" "CAMERA_READY_CLOSE_AT"; then
      apply_single_oracle_migration "025_wave3_wave6_full_closure.sql"
    fi
  fi
  if [ -x "$ROOT_DIR/scripts/demo-seed.sh" ] && command -v docker >/dev/null 2>&1; then
    bash "$ROOT_DIR/scripts/demo-seed.sh"
  elif [ -x "$ROOT_DIR/scripts/oracle-demo-seed.sh" ] && command -v docker >/dev/null 2>&1; then
    bash "$ROOT_DIR/scripts/oracle-demo-seed.sh"
  fi
  (
    cd "$ROOT_DIR/apps/api"
    mvn "${MAVEN_ARGS[@]}" test
  )
  echo "[full] api verification"
else
  echo "[skip] api tests: mvn/java not available"
fi

if command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  if "$PYTHON_BIN" -c 'import fastapi, uvicorn, pytest' >/dev/null 2>&1; then
    (
      cd "$ROOT_DIR/services/agent"
      "$PYTHON_BIN" -m pytest tests/
    )
    echo "[full] agent verification"
  else
    (
      cd "$ROOT_DIR/services/agent"
      "$PYTHON_BIN" -B -c 'import ast, pathlib; [ast.parse(path.read_text()) for path in (pathlib.Path("app/main.py"), pathlib.Path("tests/test_health.py"))]'
    )
    echo "[partial] agent syntax-only: fastapi/uvicorn/pytest missing"
  fi
else
  echo "[skip] agent verification: python3 missing"
fi

if command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
  if [ -x "$ROOT_DIR/apps/web/node_modules/.bin/vite" ]; then
    (
      cd "$ROOT_DIR/apps/web"
      npm run test -- --run
      npm run typecheck
      npm run build
    )
    echo "[full] web verification"
  else
    (
      cd "$ROOT_DIR/apps/web"
      test -f src/main.ts
      test -f src/App.vue
    )
    echo "[partial] web syntax-only: node_modules missing"
  fi
else
  echo "[skip] web verification: node/npm missing"
fi

if [ "${RUN_ANALYSIS_E2E_SMOKE:-0}" = "1" ]; then
  bash "$ROOT_DIR/scripts/analysis-e2e-smoke.sh"
  echo "[full] analysis Oracle/RabbitMQ e2e smoke"
else
  echo "[skip] analysis Oracle/RabbitMQ e2e smoke: set RUN_ANALYSIS_E2E_SMOKE=1"
fi
