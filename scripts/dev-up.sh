#!/usr/bin/env bash
set -euo pipefail

export AGENT_INTERNAL_API_KEY="local-dev-internal-key"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_LOG="/tmp/review-api.log"
AGENT_LOG="/tmp/review-agent.log"
WEB_LOG="/tmp/review-web.log"
PYTHON_BIN="python3"
MAVEN_ARGS=(-Dmaven.repo.local="$ROOT_DIR/.m2/repository")
DEFAULT_ORACLE_HOST="${ORACLE_HOST:-localhost}"
DEFAULT_ORACLE_PORT="${ORACLE_PORT:-1521}"
DEFAULT_ORACLE_SERVICE="${ORACLE_SERVICE:-FREEPDB1}"
DEFAULT_ORACLE_CONTAINER="${ORACLE_CONTAINER_NAME:-review-oracle}"
DEFAULT_ORACLE_APP_USER="${APP_USER:-review_app}"
DEFAULT_ORACLE_APP_PASSWORD="${APP_USER_PASSWORD:-ReviewApp12345}"

if [ -d "/opt/homebrew/opt/openjdk/bin" ]; then
  export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
fi

if [ -x "$ROOT_DIR/.venv/bin/python" ]; then
  PYTHON_BIN="$ROOT_DIR/.venv/bin/python"
fi

uses_default_local_oracle() {
  local oracle_service_upper
  oracle_service_upper="$(printf '%s' "$DEFAULT_ORACLE_SERVICE" | tr '[:lower:]' '[:upper:]')"
  [[ "$DEFAULT_ORACLE_HOST" == "localhost" || "$DEFAULT_ORACLE_HOST" == "127.0.0.1" ]] &&
    [[ "$DEFAULT_ORACLE_PORT" == "1521" ]] &&
    [[ "$oracle_service_upper" == "FREEPDB1" ]]
}

verify_oracle_schema() {
  if ! docker ps --format '{{.Names}}' | grep -qx "$DEFAULT_ORACLE_CONTAINER"; then
    return 1
  fi

  docker cp "$ROOT_DIR/database/oracle/verify_schema.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/verify_schema.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/verify_schema.sql" \
    >/dev/null
}

oracle_table_exists() {
  local table_name="$1"
  local table_count

  table_count="$(docker exec -i "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = UPPER('${table_name}');
EXIT;
SQL
)"

  [[ "$table_count" == "1" ]]
}

apply_oracle_refactor_schema() {
  docker cp "$ROOT_DIR/database/oracle/008_agent_platform_refactor.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/008_agent_platform_refactor.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/008_agent_platform_refactor.sql" \
    >/dev/null
}

apply_oracle_attempt_count_schema() {
  docker cp "$ROOT_DIR/database/oracle/009_execution_job_attempt_count.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/009_execution_job_attempt_count.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/009_execution_job_attempt_count.sql" \
    >/dev/null
}

apply_oracle_query_optimization_schema() {
  docker cp "$ROOT_DIR/database/oracle/010_database_query_optimization.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/010_database_query_optimization.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/010_database_query_optimization.sql" \
    >/dev/null
}

apply_oracle_legacy_agent_retirement_schema() {
  docker cp "$ROOT_DIR/database/oracle/011_retire_legacy_agent_tables.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/011_retire_legacy_agent_tables.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/011_retire_legacy_agent_tables.sql" \
    >/dev/null
}

apply_oracle_registration_schema() {
  docker cp "$ROOT_DIR/database/oracle/012_registration_foundation.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/012_registration_foundation.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/012_registration_foundation.sql" \
    >/dev/null
}

apply_oracle_registration_token_indexes_schema() {
  docker cp "$ROOT_DIR/database/oracle/013_registration_token_indexes.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/013_registration_token_indexes.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/013_registration_token_indexes.sql" \
    >/dev/null
}

apply_oracle_conference_cfp_lifecycle_schema() {
  docker cp "$ROOT_DIR/database/oracle/014_conference_cfp_lifecycle.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/014_conference_cfp_lifecycle.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/014_conference_cfp_lifecycle.sql" \
    >/dev/null
}

apply_oracle_conference_scoped_submission_schema() {
  docker cp "$ROOT_DIR/database/oracle/015_conference_scoped_submission.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/015_conference_scoped_submission.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/015_conference_scoped_submission.sql" \
    >/dev/null
}

apply_oracle_reviewer_pool_bidding_schema() {
  docker cp "$ROOT_DIR/database/oracle/016_reviewer_pool_bidding.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/016_reviewer_pool_bidding.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/016_reviewer_pool_bidding.sql" \
    >/dev/null
}

apply_oracle_assignment_drafts_schema() {
  docker cp "$ROOT_DIR/database/oracle/017_assignment_drafts.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/017_assignment_drafts.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/017_assignment_drafts.sql" \
    >/dev/null
}

apply_oracle_reviewer_assignment_assist_schema() {
  docker cp "$ROOT_DIR/database/oracle/018_reviewer_assignment_assist_analysis.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/018_reviewer_assignment_assist_analysis.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/018_reviewer_assignment_assist_analysis.sql" \
    >/dev/null
}

apply_oracle_execution_job_governance_schema() {
  docker cp "$ROOT_DIR/database/oracle/019_execution_job_governance.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/019_execution_job_governance.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/019_execution_job_governance.sql" \
    >/dev/null
}

apply_oracle_business_operations_closure_schema() {
  docker cp "$ROOT_DIR/database/oracle/020_business_operations_closure.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/020_business_operations_closure.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/020_business_operations_closure.sql" \
    >/dev/null
}

apply_oracle_real_platform_wave6_wave7_schema() {
  docker cp "$ROOT_DIR/database/oracle/021_real_platform_wave6_wave7.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/021_real_platform_wave6_wave7.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/021_real_platform_wave6_wave7.sql" \
    >/dev/null
}

apply_oracle_assignment_coi_maturity_schema() {
  docker cp "$ROOT_DIR/database/oracle/022_assignment_coi_maturity.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/022_assignment_coi_maturity.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/022_assignment_coi_maturity.sql" \
    >/dev/null
}

apply_oracle_publication_communication_maturity_schema() {
  docker cp "$ROOT_DIR/database/oracle/023_publication_communication_maturity.sql" "$DEFAULT_ORACLE_CONTAINER:/tmp/023_publication_communication_maturity.sql" >/dev/null
  docker exec "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE} @/tmp/023_publication_communication_maturity.sql" \
    >/dev/null
}

oracle_column_exists() {
  local table_name="$1"
  local column_name="$2"
  local column_count

  column_count="$(docker exec -i "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = UPPER('${table_name}') AND COLUMN_NAME = UPPER('${column_name}');
EXIT;
SQL
)"

  [[ "$column_count" == "1" ]]
}

oracle_index_exists() {
  local index_name="$1"
  local index_count

  index_count="$(docker exec -i "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = UPPER('${index_name}');
EXIT;
SQL
)"

  [[ "$index_count" == "1" ]]
}

all_query_optimization_indexes_exist() {
  oracle_index_exists "IDX_REVIEW_ROUND_STATUS_ID" &&
    oracle_index_exists "IDX_REVIEW_ASSIGNMENT_ROUND_ID" &&
    oracle_index_exists "IDX_REVIEW_ASSIGNMENT_ACCESS" &&
    oracle_index_exists "IDX_REVIEW_REPORT_ROUND" &&
    oracle_index_exists "IDX_CONFLICT_CHECK_MANUSCRIPT_REVIEWER" &&
    oracle_index_exists "IDX_MANUSCRIPT_STATUS_SUBMITTED" &&
    oracle_index_exists "IDX_ANALYSIS_PROJECTION_UPDATED"
}

all_real_platform_wave6_wave7_tables_exist() {
  oracle_table_exists "CONFERENCE_FORM_DEFINITION" &&
    oracle_table_exists "CONFERENCE_FORM_FIELD" &&
    oracle_table_exists "REVIEW_FORM_RESPONSE" &&
    oracle_table_exists "AUTHOR_FEEDBACK" &&
    oracle_table_exists "PAPER_TAG" &&
    oracle_table_exists "IMPORT_BATCH" &&
    oracle_table_exists "PAPER_ROLE_ASSIGNMENT"
}

all_assignment_coi_maturity_tables_exist() {
  oracle_table_exists "REVIEWER_INVITATION" &&
    oracle_table_exists "EXTERNAL_REVIEWER_DELEGATION" &&
    oracle_table_exists "CONFLICT_RELATIONSHIP" &&
    oracle_table_exists "REVIEWER_MATCHING_SCORE" &&
    oracle_table_exists "ASSIGNMENT_PROPOSAL_BUNDLE" &&
    oracle_table_exists "ASSIGNMENT_PROPOSAL" &&
    oracle_table_exists "ASSIGNMENT_OVERRIDE_AUDIT"
}

all_publication_communication_maturity_tables_exist() {
  oracle_table_exists "EMAIL_TEMPLATE" &&
    oracle_table_exists "EMAIL_TEMPLATE_VERSION" &&
    oracle_table_exists "OUTBOUND_EMAIL_HISTORY" &&
    oracle_table_exists "OFFLINE_REVIEW_IMPORT_BATCH" &&
    oracle_table_exists "OFFLINE_REVIEW_IMPORT_ROW" &&
    oracle_table_exists "CAMERA_READY_FILE" &&
    oracle_table_exists "PUBLICATION_METADATA" &&
    oracle_table_exists "PROCEEDINGS_EXPORT_BATCH"
}

oracle_constraint_mentions() {
  local constraint_name="$1"
  local expected_text="$2"
  local constraint_count

  constraint_count="$(docker exec -i "$DEFAULT_ORACLE_CONTAINER" bash -lc \
    "sqlplus -s ${DEFAULT_ORACLE_APP_USER}/${DEFAULT_ORACLE_APP_PASSWORD}@localhost/${DEFAULT_ORACLE_SERVICE}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT COUNT(*)
FROM USER_CONSTRAINTS
WHERE CONSTRAINT_NAME = UPPER('${constraint_name}')
  AND SEARCH_CONDITION_VC LIKE '%' || '${expected_text}' || '%';
EXIT;
SQL
)"

  [[ "$constraint_count" == "1" ]]
}

ensure_oracle_schema() {
  if verify_oracle_schema; then
    return 0
  fi

  if ! oracle_table_exists "SYS_USER"; then
    echo "Oracle base schema missing. Applying full local schema..." >&2
    CONTAINER_NAME="$DEFAULT_ORACLE_CONTAINER" bash "$ROOT_DIR/scripts/oracle-schema-apply.sh"
    return 0
  fi

  if ! oracle_table_exists "ANALYSIS_INTENT"; then
    echo "Oracle base schema detected without 008 refactor objects. Applying incremental schema..." >&2
    apply_oracle_refactor_schema
  fi

  if ! oracle_column_exists "EXECUTION_JOB" "ATTEMPT_COUNT"; then
    echo "Oracle analysis schema detected without 009 attempt-count column. Applying incremental schema..." >&2
    apply_oracle_attempt_count_schema
  fi

  if ! all_query_optimization_indexes_exist; then
    echo "Oracle schema detected without 010 query-optimization indexes. Applying incremental schema..." >&2
    apply_oracle_query_optimization_schema
  fi

  if oracle_table_exists "AGENT_ANALYSIS_TASK"; then
    echo "Oracle schema detected with retired legacy AGENT_* tables. Applying retirement schema..." >&2
    apply_oracle_legacy_agent_retirement_schema
  fi

  if ! oracle_table_exists "ROLE_APPLICATION"; then
    echo "Oracle schema detected without 012 registration foundation objects. Applying incremental schema..." >&2
    apply_oracle_registration_schema
  fi

  if oracle_table_exists "EMAIL_VERIFICATION_TOKEN" && ! oracle_index_exists "IDX_EMAIL_VERIFICATION_EXPIRES"; then
    echo "Oracle schema detected without 013 registration token indexes. Applying incremental schema..." >&2
    apply_oracle_registration_token_indexes_schema
  fi

  if ! oracle_table_exists "CONFERENCE"; then
    echo "Oracle schema detected without 014 conference CFP lifecycle objects. Applying incremental schema..." >&2
    apply_oracle_conference_cfp_lifecycle_schema
  fi

  if oracle_table_exists "MANUSCRIPT" && ! oracle_column_exists "MANUSCRIPT" "CONFERENCE_ID"; then
    echo "Oracle schema detected without 015 conference-scoped submission objects. Applying incremental schema..." >&2
    apply_oracle_conference_scoped_submission_schema
  fi

  if ! oracle_table_exists "CONFERENCE_REVIEWER"; then
    echo "Oracle schema detected without 016 reviewer-pool bidding objects. Applying incremental schema..." >&2
    apply_oracle_reviewer_pool_bidding_schema
  fi

  if ! oracle_table_exists "ASSIGNMENT_DRAFT"; then
    echo "Oracle schema detected without 017 assignment draft objects. Applying incremental schema..." >&2
    apply_oracle_assignment_drafts_schema
  fi

  if ! oracle_constraint_mentions "CK_ANALYSIS_INTENT_TYPE" "REVIEWER_ASSIGNMENT_ASSIST"; then
    echo "Oracle schema detected without 018 reviewer assignment assist analysis type. Applying incremental schema..." >&2
    apply_oracle_reviewer_assignment_assist_schema
  fi

  if ! oracle_column_exists "EXECUTION_JOB" "LAST_ERROR_CATEGORY" ||
    ! oracle_column_exists "EXECUTION_JOB" "LAST_ATTEMPT_AT" ||
    ! oracle_column_exists "EXECUTION_JOB" "COMPLETED_AT" ||
    ! oracle_index_exists "IDX_EXECUTION_JOB_ERROR_UPDATED" ||
    ! oracle_index_exists "IDX_EXECUTION_JOB_ATTEMPT_AT"; then
    echo "Oracle schema detected without 019 execution job governance fields. Applying incremental schema..." >&2
    apply_oracle_execution_job_governance_schema
  fi

  if ! oracle_table_exists "REVIEW_DISCUSSION_MESSAGE" ||
    ! oracle_table_exists "CAMERA_READY_SUBMISSION" ||
    ! oracle_table_exists "COMMUNICATION_LOG"; then
    echo "Oracle schema detected without 020 business operations closure objects. Applying incremental schema..." >&2
    apply_oracle_business_operations_closure_schema
  fi

  if ! all_real_platform_wave6_wave7_tables_exist; then
    echo "Oracle schema detected without 021 real-platform Wave6/Wave7 objects. Applying incremental schema..." >&2
    apply_oracle_real_platform_wave6_wave7_schema
  fi

  if ! all_assignment_coi_maturity_tables_exist; then
    echo "Oracle schema detected without 022 assignment/COI maturity objects. Applying incremental schema..." >&2
    apply_oracle_assignment_coi_maturity_schema
  fi

  if ! all_publication_communication_maturity_tables_exist; then
    echo "Oracle schema detected without 023 publication/communication maturity objects. Applying incremental schema..." >&2
    apply_oracle_publication_communication_maturity_schema
  fi

  if verify_oracle_schema; then
    return 0
  fi

  echo "Oracle schema exists but failed verification." >&2
  return 1
}

ensure_oracle() {
  if ! uses_default_local_oracle; then
    echo "Skipping Oracle bootstrap: ORACLE_* points to a non-default instance." >&2
    return 0
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "Skipping API: docker is required to bootstrap the default local Oracle instance." >&2
    return 1
  fi

  CONTAINER_NAME="$DEFAULT_ORACLE_CONTAINER" bash "$ROOT_DIR/scripts/oracle-up.sh"

  if ! ensure_oracle_schema; then
    return 1
  fi

  if ! verify_oracle_schema; then
    echo "Oracle schema verification failed after bootstrap." >&2
    return 1
  fi

  if [ -x "$ROOT_DIR/scripts/demo-seed.sh" ]; then
    CONTAINER_NAME="$DEFAULT_ORACLE_CONTAINER" bash "$ROOT_DIR/scripts/demo-seed.sh"
  elif [ -x "$ROOT_DIR/scripts/oracle-demo-seed.sh" ]; then
    CONTAINER_NAME="$DEFAULT_ORACLE_CONTAINER" bash "$ROOT_DIR/scripts/oracle-demo-seed.sh"
  fi
}

start_api() {
  if ! command -v mvn >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "Skipping API: mvn or java is not installed." >&2
    return 1
  fi
  local old_pwd="$PWD"
  cd "$ROOT_DIR/apps/api"
  mvn "${MAVEN_ARGS[@]}" spring-boot:run >"$API_LOG" 2>&1 &
  PIDS+=("$!")
  cd "$old_pwd"
}

start_agent() {
  if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
    echo "Skipping agent: python is not installed." >&2
    return 1
  fi
  if ! "$PYTHON_BIN" -c 'import fastapi, uvicorn' >/dev/null 2>&1; then
    echo "Skipping agent: fastapi/uvicorn are not installed." >&2
    return 1
  fi
  local old_pwd="$PWD"
  cd "$ROOT_DIR/services/agent"
  "$PYTHON_BIN" -m uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload >"$AGENT_LOG" 2>&1 &
  PIDS+=("$!")
  cd "$old_pwd"
}

start_rabbitmq() {
  bash "$ROOT_DIR/scripts/rabbitmq-up.sh" --optional
}

start_web() {
  if ! command -v npm >/dev/null 2>&1; then
    echo "Skipping web: npm is not installed." >&2
    return 1
  fi
  if [ ! -x "$ROOT_DIR/apps/web/node_modules/.bin/vite" ]; then
    echo "Skipping web: node_modules are not installed." >&2
    return 1
  fi
  local old_pwd="$PWD"
  cd "$ROOT_DIR/apps/web"
  npm run dev -- --host 0.0.0.0 --port 5173 >"$WEB_LOG" 2>&1 &
  PIDS+=("$!")
  cd "$old_pwd"
}

PIDS=()
cleanup() {
  if [ "${#PIDS[@]}" -gt 0 ]; then
    kill "${PIDS[@]}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

if ! start_rabbitmq; then
  echo "Skipping RabbitMQ: optional bootstrap failed." >&2
fi
if ensure_oracle; then
  start_api || true
else
  echo "Skipping API: Oracle bootstrap failed." >&2
fi
start_agent || true
start_web || true

if [ "${#PIDS[@]}" -eq 0 ]; then
  echo "No services started." >&2
  exit 1
fi

wait
