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
