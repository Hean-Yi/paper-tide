#!/usr/bin/env bash
set -euo pipefail

MODE="${1:---optional}"
if [ "$#" -gt 1 ]; then
  echo "Usage: $0 [--optional|--required]" >&2
  exit 2
fi

case "$MODE" in
  --optional|optional)
    REQUIRED=0
    ;;
  --required|required)
    REQUIRED=1
    ;;
  *)
    echo "Usage: $0 [--optional|--required]" >&2
    exit 2
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  if [ "$REQUIRED" -eq 1 ]; then
    echo "RabbitMQ bootstrap requires docker, but docker is not installed." >&2
    exit 1
  fi
  echo "Skipping RabbitMQ bootstrap: docker is not installed." >&2
  exit 0
fi

if ! docker info >/dev/null 2>&1; then
  if [ "$REQUIRED" -eq 1 ]; then
    echo "RabbitMQ bootstrap requires a usable Docker engine, but docker is unavailable or the daemon is unreachable." >&2
    exit 1
  fi
  echo "Skipping RabbitMQ bootstrap: Docker is installed but the engine is unavailable or unreachable." >&2
  exit 0
fi

CONTAINER_NAME="${CONTAINER_NAME:-review-rabbitmq}"
READY_TIMEOUT_SECONDS="${RABBITMQ_READY_TIMEOUT_SECONDS:-30}"
STARTUP_GRACE_SECONDS="${RABBITMQ_STARTUP_GRACE_SECONDS:-2}"
BOOTSTRAP_RETRIES="${RABBITMQ_BOOTSTRAP_RETRIES:-2}"
IMAGE="${RABBITMQ_IMAGE:-rabbitmq:3-management}"

container_exists() {
  docker inspect "$CONTAINER_NAME" >/dev/null 2>&1
}

container_status() {
  docker inspect -f '{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "missing"
}

remove_container() {
  if container_exists; then
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
}

start_container() {
  if container_exists; then
    local status
    status="$(container_status)"
    case "$status" in
      running)
        return 0
        ;;
      created|exited|dead)
        docker start "$CONTAINER_NAME" >/dev/null
        ;;
      *)
        remove_container
        docker run -d --name "$CONTAINER_NAME" -p 5672:5672 -p 15672:15672 "$IMAGE" >/dev/null
        ;;
    esac
  else
    docker run -d --name "$CONTAINER_NAME" -p 5672:5672 -p 15672:15672 "$IMAGE" >/dev/null
  fi
}

wait_for_ready() {
  local deadline status

  deadline=$((SECONDS + READY_TIMEOUT_SECONDS))
  while true; do
    if docker exec "$CONTAINER_NAME" rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
      return 0
    fi

    status="$(container_status)"
    case "$status" in
      created|running|restarting)
        ;;
      exited|dead)
        echo "RabbitMQ container '$CONTAINER_NAME' stopped before it became ready." >&2
        docker logs "$CONTAINER_NAME" >&2 || true
        return 1
        ;;
      missing)
        echo "RabbitMQ container '$CONTAINER_NAME' disappeared before it became ready." >&2
        return 1
        ;;
      *)
        echo "RabbitMQ container '$CONTAINER_NAME' entered unexpected state '$status'." >&2
        docker logs "$CONTAINER_NAME" >&2 || true
        return 1
        ;;
    esac

    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for RabbitMQ container '$CONTAINER_NAME' to become ready." >&2
      docker logs "$CONTAINER_NAME" >&2 || true
      return 1
    fi

    sleep 1
  done
}

for attempt in $(seq 1 "$BOOTSTRAP_RETRIES"); do
  start_container
  sleep "$STARTUP_GRACE_SECONDS"

  if wait_for_ready; then
    exit 0
  fi

  if [ "$attempt" -lt "$BOOTSTRAP_RETRIES" ]; then
    echo "RabbitMQ bootstrap attempt $attempt failed. Retrying with a clean container..." >&2
    remove_container
  fi
done

exit 1
