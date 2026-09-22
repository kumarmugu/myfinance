#!/bin/bash
# Start MyFinance in PRODUCTION mode
# Usage: bash scripts/start-prod.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"

# Load local secrets (untracked). scripts/.env.prod holds env vars like CREDENTIAL_MASTER_KEY.
# `set -a` exports everything sourced so the backend process inherits it.
ENV_FILE="$SCRIPT_DIR/.env.prod"
if [ -f "$ENV_FILE" ]; then
    echo "Loading secrets from scripts/.env.prod"
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

# Broker credential encryption must be configured in production so secrets are never stored as
# plaintext. Fail loudly rather than silently starting with encryption disabled.
if [ -z "${CREDENTIAL_MASTER_KEY:-}" ]; then
    echo "ERROR: CREDENTIAL_MASTER_KEY is not set." >&2
    echo "       Broker credential storage would be disabled and any save would be rejected." >&2
    echo "       Set it in scripts/.env.prod (see scripts/.env.prod.example) or export it, then re-run." >&2
    echo "       Generate one with: openssl rand -base64 32" >&2
    exit 1
fi

echo "═══════════════════════════════════════════"
echo "  MyFinance — Production Mode"
echo "═══════════════════════════════════════════"
echo ""
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo "  DB:       ./backend/data-prod/myfinance"
echo "  Logs:     ./backend/logs/myfinance-prod.log"
echo ""
echo "═══════════════════════════════════════════"
echo ""

# Start backend
echo "[1/2] Starting backend (prod profile)..."
cd "$PROJECT_DIR/backend"
JAVA_HOME="$JAVA_HOME" ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod &
BACKEND_PID=$!

# Wait for backend to be ready
echo "      Waiting for backend..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/api/auth/login > /dev/null 2>&1; then
        echo "      Backend ready!"
        break
    fi
    sleep 1
done

# Start frontend
echo "[2/2] Starting frontend..."
cd "$PROJECT_DIR/frontend"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "Both services started."
echo "  Backend PID:  $BACKEND_PID"
echo "  Frontend PID: $FRONTEND_PID"
echo ""
echo "Press Ctrl+C to stop both."

# Trap Ctrl+C to kill both
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; echo 'Stopped.'; exit 0" INT TERM
wait
