#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-review-oracle}"
IMAGE="${IMAGE:-gvenzl/oracle-free:23-slim-faststart}"
ORACLE_PASSWORD="${ORACLE_PASSWORD:-Review12345}"
APP_USER="${APP_USER:-review_app}"
APP_USER_PASSWORD="${APP_USER_PASSWORD:-ReviewApp12345}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "Oracle container '$CONTAINER_NAME' is already running."
  exit 0
fi

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  docker start "$CONTAINER_NAME" >/dev/null
else
  docker run -d \
    --name "$CONTAINER_NAME" \
    -p 1521:1521 \
    -p 5500:5500 \
    -e ORACLE_PASSWORD="$ORACLE_PASSWORD" \
    -e APP_USER="$APP_USER" \
    -e APP_USER_PASSWORD="$APP_USER_PASSWORD" \
    "$IMAGE" >/dev/null
fi

echo "Waiting for Oracle to become ready..."
for _ in $(seq 1 90); do
  if docker logs "$CONTAINER_NAME" 2>&1 | tail -n 50 | grep -q "DATABASE IS READY TO USE"; then
    echo "Oracle is ready."
    exit 0
  fi
  sleep 5
done

echo "Oracle did not become ready in time." >&2
exit 1
