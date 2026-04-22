#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "RabbitMQ bootstrap requires docker, but docker is not installed." >&2
  exit 1
fi

CONTAINER_NAME="${CONTAINER_NAME:-review-rabbitmq}"
READY_TIMEOUT_SECONDS="${RABBITMQ_READY_TIMEOUT_SECONDS:-30}"

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
    docker start "$CONTAINER_NAME" >/dev/null
  fi
else
  docker run -d --rm --name "$CONTAINER_NAME" -p 5672:5672 -p 15672:15672 rabbitmq:3-management >/dev/null
fi

deadline=$((SECONDS + READY_TIMEOUT_SECONDS))
while true; do
  if docker exec "$CONTAINER_NAME" rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
    exit 0
  fi

  if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
    echo "RabbitMQ container '$CONTAINER_NAME' stopped before it became ready." >&2
    docker logs "$CONTAINER_NAME" >&2 || true
    exit 1
  fi

  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "Timed out waiting for RabbitMQ container '$CONTAINER_NAME' to become ready." >&2
    docker logs "$CONTAINER_NAME" >&2 || true
    exit 1
  fi

  sleep 1
done
