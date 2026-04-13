#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER_NAME="${CONTAINER_NAME:-review-oracle}"
APP_USER="${APP_USER:-review_app}"
APP_USER_PASSWORD="${APP_USER_PASSWORD:-ReviewApp12345}"
ORACLE_SERVICE="${ORACLE_SERVICE:-FREEPDB1}"
SCRIPT_NAME="007_seed_demo_workflow.sql"

if [ -x "$ROOT_DIR/scripts/oracle-demo-seed.sh" ]; then
  bash "$ROOT_DIR/scripts/oracle-demo-seed.sh"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "Oracle container '$CONTAINER_NAME' is not running." >&2
  exit 1
fi

docker cp "$ROOT_DIR/database/oracle/$SCRIPT_NAME" "$CONTAINER_NAME:/tmp/$SCRIPT_NAME"

docker exec "$CONTAINER_NAME" bash -lc "
sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost/${ORACLE_SERVICE} @/tmp/${SCRIPT_NAME}
"
