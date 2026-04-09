#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER_NAME="${CONTAINER_NAME:-review-oracle}"
APP_USER="${APP_USER:-review_app}"
APP_USER_PASSWORD="${APP_USER_PASSWORD:-ReviewApp12345}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "Oracle container '$CONTAINER_NAME' is not running." >&2
  exit 1
fi

docker cp "$ROOT_DIR/database/oracle" "$CONTAINER_NAME:/tmp/review-db"

docker exec "$CONTAINER_NAME" bash -lc "
sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost/FREEPDB1 @/tmp/review-db/001_init.sql &&
sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost/FREEPDB1 @/tmp/review-db/002_seed_roles.sql &&
sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost/FREEPDB1 @/tmp/review-db/003_indexes.sql &&
sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost/FREEPDB1 @/tmp/review-db/004_procedures.sql &&
sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost/FREEPDB1 @/tmp/review-db/005_triggers.sql &&
sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost/FREEPDB1 @/tmp/review-db/verify_schema.sql
"
