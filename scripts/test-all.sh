#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="python3"
MAVEN_ARGS=(-Dmaven.repo.local="$ROOT_DIR/.m2/repository")

if [ -d "/opt/homebrew/opt/openjdk/bin" ]; then
  export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
fi

if [ -x "$ROOT_DIR/.venv/bin/python" ]; then
  PYTHON_BIN="$ROOT_DIR/.venv/bin/python"
fi

if command -v mvn >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  bash "$ROOT_DIR/scripts/rabbitmq-up.sh"
  if [ -x "$ROOT_DIR/scripts/demo-seed.sh" ] && command -v docker >/dev/null 2>&1; then
    bash "$ROOT_DIR/scripts/demo-seed.sh"
  elif [ -x "$ROOT_DIR/scripts/oracle-demo-seed.sh" ] && command -v docker >/dev/null 2>&1; then
    bash "$ROOT_DIR/scripts/oracle-demo-seed.sh"
  fi
  (
    cd "$ROOT_DIR/apps/api"
    mvn "${MAVEN_ARGS[@]}" test
  )
  echo "[full] api verification"
else
  echo "[skip] api tests: mvn/java not available"
fi

if command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  if "$PYTHON_BIN" -c 'import fastapi, uvicorn, pytest' >/dev/null 2>&1; then
    (
      cd "$ROOT_DIR/services/agent"
      "$PYTHON_BIN" -m pytest tests/test_health.py
    )
    echo "[full] agent verification"
  else
    (
      cd "$ROOT_DIR/services/agent"
      "$PYTHON_BIN" -B -c 'import ast, pathlib; [ast.parse(path.read_text()) for path in (pathlib.Path("app/main.py"), pathlib.Path("tests/test_health.py"))]'
    )
    echo "[partial] agent syntax-only: fastapi/uvicorn/pytest missing"
  fi
else
  echo "[skip] agent verification: python3 missing"
fi

if command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
  if [ -x "$ROOT_DIR/apps/web/node_modules/.bin/vite" ]; then
    (
      cd "$ROOT_DIR/apps/web"
      npm run test -- --run
      npm run typecheck
      npm run build
    )
    echo "[full] web verification"
  else
    (
      cd "$ROOT_DIR/apps/web"
      test -f src/main.ts
      test -f src/App.vue
    )
    echo "[partial] web syntax-only: node_modules missing"
  fi
else
  echo "[skip] web verification: node/npm missing"
fi
