#!/usr/bin/env bash
set -euo pipefail


export AGENT_INTERNAL_API_KEY="local-dev-internal-key"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_LOG="/tmp/review-api.log"
AGENT_LOG="/tmp/review-agent.log"
WEB_LOG="/tmp/review-web.log"
PYTHON_BIN="python3"
MAVEN_ARGS=(-Dmaven.repo.local="$ROOT_DIR/.m2/repository")

if [ -d "/opt/homebrew/opt/openjdk/bin" ]; then
  export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
fi

if [ -x "$ROOT_DIR/.venv/bin/python" ]; then
  PYTHON_BIN="$ROOT_DIR/.venv/bin/python"
fi

start_api() {
  if ! command -v mvn >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "Skipping API: mvn or java is not installed." >&2
    return 1
  fi
  local old_pwd="$PWD"
  cd "$ROOT_DIR/apps/api"
  AGENT_INTERNAL_API_KEY="$AGENT_INTERNAL_API_KEY" mvn "${MAVEN_ARGS[@]}" spring-boot:run >"$API_LOG" 2>&1 &
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
  AGENT_INTERNAL_API_KEY="$AGENT_INTERNAL_API_KEY" "$PYTHON_BIN" -m uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload >"$AGENT_LOG" 2>&1 &
  PIDS+=("$!")
  cd "$old_pwd"
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

start_api || true
start_agent || true
start_web || true

if [ "${#PIDS[@]}" -eq 0 ]; then
  echo "No services started." >&2
  exit 1
fi

wait
