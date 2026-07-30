#!/bin/bash
# MyFinance Application Startup Script
# Starts both backend (Spring Boot) and frontend (Vite dev server)

set -e

echo "========================================="
echo "  MyFinance - Personal Finance Manager"
echo "========================================="
echo ""

# Detect Java
if [ -n "$JAVA_HOME" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Detect Node
if [ -d "$HOME/local/node/bin" ]; then
  export PATH="$HOME/local/node/bin:$PATH"
fi

# Start Backend
echo "[1/2] Starting Spring Boot backend on port 8080..."
cd backend
./mvnw spring-boot:run &
BACKEND_PID=$!
cd ..

# Wait for backend to be ready
echo "      Waiting for backend to start..."
sleep 10

# Start Frontend
echo "[2/2] Starting Vite frontend on port 5173..."
cd frontend
npm run dev &
FRONTEND_PID=$!
cd ..

echo ""
echo "========================================="
echo "  Application started!"
echo "  Frontend: http://localhost:5173"
echo "  Backend:  http://localhost:8080"
echo "  H2 Console: http://localhost:8080/h2-console"
echo "========================================="
echo ""
echo "Press Ctrl+C to stop both servers"

# Cleanup on exit
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM
wait
