#!/usr/bin/env bash
# Convenience launcher for local development without Docker.
# Usage: ./scripts/dev-up.sh   (expects PostgreSQL running locally; see README)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ -f "$ROOT/.env" ]; then
  set -a; source "$ROOT/.env"; set +a
fi

echo "Starting backend on :${SERVER_PORT:-8081}..."
cd "$ROOT/backend"
SERVER_PORT="${SERVER_PORT:-8081}" DB_USER="${DB_USER:?set DB_USER in .env}" \
  mvn -q spring-boot:run &
BACKEND_PID=$!

echo "Starting frontend on :3000..."
cd "$ROOT/frontend"
VITE_API_PROXY_TARGET="http://localhost:${SERVER_PORT:-8081}" npx vite --port 3000 --strictPort &
FRONTEND_PID=$!

cleanup() {
  echo "Stopping..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
wait
