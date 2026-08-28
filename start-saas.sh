#!/bin/bash
# MyFinance SaaS Platform Startup Script
# Starts the SaaS backend (Spring Boot, port 8090) and the SaaS marketing website
# (Vite dev server, port 5174). This is SEPARATE from the finance app (start.sh).
#
# Usage:
#   ./start-saas.sh
#
# Notes:
#   - Secrets (PROVISIONING_TOKEN, STRIPE_*, SAAS_JWT_SECRET, SMTP_*) are read from the
#     environment. With none set, signup/marketing work but provisioning + real payments
#     stay inert (email provider defaults to "noop").
#   - The "Login" button on the site points at the finance app (default http://localhost:5173),
#     so run ./start.sh too if you want those links to resolve.

set -e

echo "========================================="
echo "  MyFinance SaaS Platform"
echo "========================================="
echo ""

# Java 17 is required (JDK 26 breaks tests/runtime with an ICU error).
if [ -z "$JAVA_HOME" ] && [ -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" ]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
fi
if [ -n "$JAVA_HOME" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Detect Node (mirrors start.sh)
if [ -d "$HOME/local/node/bin" ]; then
  export PATH="$HOME/local/node/bin:$PATH"
fi

# Start SaaS backend
echo "[1/2] Starting SaaS backend on port 8090..."
cd saas-backend
./mvnw spring-boot:run &
SAAS_BACKEND_PID=$!
cd ..

# Wait for backend to be ready
echo "      Waiting for SaaS backend to start..."
sleep 12

# Install web deps on first run
if [ ! -d "saas-web/node_modules" ]; then
  echo "      Installing saas-web dependencies (first run)..."
  (cd saas-web && npm install)
fi

# Start SaaS web
echo "[2/2] Starting SaaS website on port 5174..."
cd saas-web
npm run dev &
SAAS_WEB_PID=$!
cd ..

echo ""
echo "========================================="
echo "  SaaS platform started!"
echo "  Website:  http://localhost:5174"
echo "  Backend:  http://localhost:8090"
echo "  Health:   http://localhost:8090/actuator/health"
echo "  API docs: http://localhost:8090/swagger-ui.html"
echo "========================================="
echo ""
echo "Press Ctrl+C to stop both servers"

# Cleanup on exit
trap "kill $SAAS_BACKEND_PID $SAAS_WEB_PID 2>/dev/null; exit" INT TERM
wait
