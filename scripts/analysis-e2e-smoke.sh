#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
API_PORT="${API_SMOKE_PORT:-18080}"
AGENT_PORT="${AGENT_SMOKE_PORT:-18001}"
API_BASE="http://localhost:${API_PORT}/api"
AGENT_BASE="http://localhost:${AGENT_PORT}"
API_LOG="${API_SMOKE_LOG:-/tmp/review-analysis-smoke-api.log}"
AGENT_LOG="${AGENT_SMOKE_LOG:-/tmp/review-analysis-smoke-agent.log}"
MAVEN_ARGS=(-Dmaven.repo.local="$ROOT_DIR/.m2/repository")
SMOKE_EXCHANGE="${ANALYSIS_SMOKE_EXCHANGE:-review.analysis.smoke.exchange}"
SMOKE_REQUEST_QUEUE="${ANALYSIS_SMOKE_REQUEST_QUEUE:-analysis.requested.agent.smoke}"
SMOKE_REQUEST_ROUTING_KEY="${ANALYSIS_SMOKE_REQUEST_ROUTING_KEY:-analysis.requested.smoke}"
SMOKE_COMPLETION_QUEUE="${ANALYSIS_SMOKE_COMPLETION_QUEUE:-analysis.completed.api.smoke}"
SMOKE_COMPLETION_ROUTING_KEY="${ANALYSIS_SMOKE_COMPLETION_ROUTING_KEY:-analysis.completed.smoke}"

if [ -d "/opt/homebrew/opt/openjdk/bin" ]; then
  export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
fi

if [ -x "$ROOT_DIR/.venv/bin/python" ]; then
  PYTHON_BIN="$ROOT_DIR/.venv/bin/python"
fi

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "analysis smoke requires '$1'." >&2
    exit 1
  fi
}

wait_for_url() {
  local url="$1"
  local name="$2"
  local timeout_seconds="${3:-120}"
  local deadline=$((SECONDS + timeout_seconds))
  until "$PYTHON_BIN" - "$url" <<'PY' >/dev/null 2>&1
import sys
from urllib.request import urlopen

with urlopen(sys.argv[1], timeout=2) as response:
    if response.status >= 500:
        raise SystemExit(1)
PY
  do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "$name did not become ready at $url" >&2
      echo "API log: $API_LOG" >&2
      echo "Agent log: $AGENT_LOG" >&2
      exit 1
    fi
    sleep 2
  done
}

oracle_sql_count() {
  local sql="$1"
  local container_name="${ORACLE_CONTAINER_NAME:-review-oracle}"
  local app_user="${APP_USER:-review_app}"
  local app_password="${APP_USER_PASSWORD:-ReviewApp12345}"
  local oracle_service="${ORACLE_SERVICE:-FREEPDB1}"

  docker exec -i "$container_name" bash -lc \
    "sqlplus -s ${app_user}/${app_password}@localhost/${oracle_service}" <<SQL | tr -d '[:space:]'
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
${sql}
EXIT;
SQL
}

oracle_table_exists() {
  local table_name="$1"
  [[ "$(oracle_sql_count "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = UPPER('${table_name}');")" == "1" ]]
}

oracle_column_exists() {
  local table_name="$1"
  local column_name="$2"
  [[ "$(oracle_sql_count "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = UPPER('${table_name}') AND COLUMN_NAME = UPPER('${column_name}');")" == "1" ]]
}

apply_single_oracle_migration() {
  local migration="$1"
  local container_name="${ORACLE_CONTAINER_NAME:-review-oracle}"
  local app_user="${APP_USER:-review_app}"
  local app_password="${APP_USER_PASSWORD:-ReviewApp12345}"
  local oracle_service="${ORACLE_SERVICE:-FREEPDB1}"

  docker cp "$ROOT_DIR/database/oracle/$migration" "$container_name:/tmp/$migration" >/dev/null
  docker exec "$container_name" bash -lc \
    "sqlplus -s ${app_user}/${app_password}@localhost/${oracle_service} @/tmp/${migration}" \
    >/dev/null
}

PIDS=()
cleanup() {
  if [ "${#PIDS[@]}" -gt 0 ]; then
    kill "${PIDS[@]}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

require_command docker
require_command mvn
require_command java
require_command "$PYTHON_BIN"

if ! "$PYTHON_BIN" -c 'import aio_pika, oracledb, uvicorn' >/dev/null 2>&1; then
  "$PYTHON_BIN" -m pip install -e "$ROOT_DIR/services/agent"
fi

bash "$ROOT_DIR/scripts/rabbitmq-up.sh" --required
CONTAINER_NAME="${ORACLE_CONTAINER_NAME:-review-oracle}" bash "$ROOT_DIR/scripts/oracle-up.sh"
if ! oracle_table_exists "SYS_USER"; then
  CONTAINER_NAME="${ORACLE_CONTAINER_NAME:-review-oracle}" bash "$ROOT_DIR/scripts/oracle-schema-apply.sh"
elif ! oracle_column_exists "EXECUTION_JOB" "LAST_ERROR_CATEGORY" ||
  ! oracle_column_exists "EXECUTION_JOB" "LAST_ATTEMPT_AT" ||
  ! oracle_column_exists "EXECUTION_JOB" "COMPLETED_AT"; then
  apply_single_oracle_migration "019_execution_job_governance.sql"
fi
CONTAINER_NAME="${ORACLE_CONTAINER_NAME:-review-oracle}" bash "$ROOT_DIR/scripts/demo-seed.sh"

(
  cd "$ROOT_DIR/apps/api"
  SERVER_PORT="$API_PORT" \
  REVIEW_ANALYSIS_BROKER_ENABLED=true \
  REVIEW_ANALYSIS_BROKER_EXCHANGE="$SMOKE_EXCHANGE" \
  REVIEW_ANALYSIS_REQUEST_QUEUE="$SMOKE_REQUEST_QUEUE" \
  REVIEW_ANALYSIS_REQUEST_ROUTING_KEY="$SMOKE_REQUEST_ROUTING_KEY" \
  REVIEW_ANALYSIS_COMPLETION_QUEUE="$SMOKE_COMPLETION_QUEUE" \
  REVIEW_ANALYSIS_COMPLETION_ROUTING_KEY="$SMOKE_COMPLETION_ROUTING_KEY" \
  REVIEW_ANALYSIS_OUTBOX_DISPATCH_DELAY_MS=1000 \
  RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}" \
  RABBITMQ_PORT="${RABBITMQ_PORT:-5672}" \
  RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-guest}" \
  RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-guest}" \
  ORACLE_HOST="${ORACLE_HOST:-localhost}" \
  ORACLE_PORT="${ORACLE_PORT:-1521}" \
  ORACLE_SERVICE="${ORACLE_SERVICE:-FREEPDB1}" \
  ORACLE_USERNAME="${ORACLE_USERNAME:-review_app}" \
  ORACLE_PASSWORD="${ORACLE_PASSWORD:-ReviewApp12345}" \
  mvn "${MAVEN_ARGS[@]}" spring-boot:run
) >"$API_LOG" 2>&1 &
PIDS+=("$!")

(
  cd "$ROOT_DIR/services/agent"
  AGENT_PLATFORM_BROKER_ENABLED=true \
  AGENT_PLATFORM_RABBITMQ_URL="${AGENT_PLATFORM_RABBITMQ_URL:-amqp://guest:guest@localhost:5672/}" \
  AGENT_PLATFORM_BROKER_EXCHANGE="$SMOKE_EXCHANGE" \
  AGENT_PLATFORM_REQUEST_QUEUE="$SMOKE_REQUEST_QUEUE" \
  AGENT_PLATFORM_REQUEST_ROUTING_KEY="$SMOKE_REQUEST_ROUTING_KEY" \
  AGENT_PLATFORM_ANALYSIS_COMPLETED_TOPIC="$SMOKE_COMPLETION_ROUTING_KEY" \
  AGENT_PLATFORM_COMPLETED_ROUTING_KEY="$SMOKE_COMPLETION_ROUTING_KEY" \
  AGENT_PLATFORM_DB_USER="${AGENT_PLATFORM_DB_USER:-review_app}" \
  AGENT_PLATFORM_DB_PASSWORD="${AGENT_PLATFORM_DB_PASSWORD:-ReviewApp12345}" \
  AGENT_PLATFORM_DB_DSN="${AGENT_PLATFORM_DB_DSN:-localhost:1521/FREEPDB1}" \
  AGENT_PLATFORM_COMPLETED_PUBLISH_INTERVAL_SECONDS=1.0 \
  "$PYTHON_BIN" -m uvicorn app.main:app --host 127.0.0.1 --port "$AGENT_PORT"
) >"$AGENT_LOG" 2>&1 &
PIDS+=("$!")

wait_for_url "$API_BASE/health/readiness" "API readiness" 180
wait_for_url "$AGENT_BASE/health/readiness" "Agent readiness" 120

API_BASE="$API_BASE" "$PYTHON_BIN" <<'PY'
import json
import os
import sys
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

api_base = os.environ["API_BASE"]


def request(method: str, path: str, payload: dict | None = None, token: str | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json", "X-Trace-Id": "smoke-analysis-e2e"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = Request(f"{api_base}{path}", data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=10) as response:
            body = response.read().decode("utf-8")
            return json.loads(body) if body else {}
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} failed with {exc.code}: {body}") from exc


def login(username: str) -> str:
    response = request("POST", "/auth/login", {"username": username, "password": "demo123"})
    return response["token"]


chair_token = login("chair_demo")
admin_token = login("admin_demo")
intent = request(
    "POST",
    "/manuscripts/9001/versions/9001/screening-analysis",
    {"force": True},
    chair_token,
)
intent_id = int(intent["intentId"])
deadline = time.time() + 90
while time.time() < deadline:
    page = request("GET", "/admin/analysis-monitor", token=admin_token)
    rows = page.get("items", page) if isinstance(page, dict) else page
    for row in rows:
        if int(row["intentId"]) == intent_id and row["businessStatus"] == "AVAILABLE" and row.get("summaryText"):
            print(f"analysis smoke passed: intentId={intent_id} jobId={row.get('jobId')}")
            sys.exit(0)
    time.sleep(2)

raise SystemExit(f"analysis smoke timed out waiting for intentId={intent_id} to become AVAILABLE")
PY
