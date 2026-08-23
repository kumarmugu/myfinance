#!/bin/bash
# Start MyFinance in PRODUCTION mode
# Usage: bash scripts/start-prod.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"

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
