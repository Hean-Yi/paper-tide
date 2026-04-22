#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "Skipping RabbitMQ: docker is not installed." >&2
  exit 0
fi

CONTAINER_NAME="${CONTAINER_NAME:-review-rabbitmq}"

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  exit 0
fi

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  docker start "$CONTAINER_NAME" >/dev/null
else
  docker run -d --rm --name "$CONTAINER_NAME" -p 5672:5672 -p 15672:15672 rabbitmq:3-management >/dev/null
fi
