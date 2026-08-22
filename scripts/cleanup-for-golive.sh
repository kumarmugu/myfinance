#!/bin/bash
# MyFinance - Cleanup Script for Go-Live
# Removes all sample data but keeps:
#   - User accounts (admin login)
#   - Currency rates (useful reference)
#   - Net worth config
#
# Run this ONCE before entering real data.
# Usage: bash scripts/cleanup-for-golive.sh
#
# IMPORTANT: This connects to the running backend API to delete data.
# Make sure the backend is running on port 8080.

set -e

BASE_URL="${1:-http://localhost:8080}"

echo "========================================="
echo "  MyFinance - Go-Live Cleanup"
echo "========================================="
echo ""
echo "This will DELETE all sample data from the database."
echo "The following will be KEPT:"
echo "  - User account (admin login)"
echo "  - Currency rates"
echo "  - Net worth configuration"
echo ""
read -p "Are you sure? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

# Get auth token
echo ""
echo "[Auth] Logging in..."
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")

if [ -z "$TOKEN" ]; then
  echo "ERROR: Could not authenticate. Is the backend running?"
  exit 1
fi

AUTH="Authorization: Bearer $TOKEN"

echo "[Auth] Authenticated."
echo ""

# Helper function
delete_all() {
  local endpoint=$1
  local name=$2
  echo -n "  Cleaning $name..."
  IDS=$(curl -s -H "$AUTH" "$BASE_URL$endpoint" | python3 -c "import sys,json; [print(i['id']) for i in json.load(sys.stdin)]" 2>/dev/null)
  COUNT=0
  for id in $IDS; do
    curl -s -X DELETE -H "$AUTH" "$BASE_URL$endpoint/$id" > /dev/null
    COUNT=$((COUNT + 1))
  done
  echo " deleted $COUNT records"
}

echo "[Cleanup] Removing sample data..."

# Order matters due to foreign keys
delete_all "/api/salary" "Salary Records"
delete_all "/api/tax" "Tax Records"
delete_all "/api/work-experience" "Work Experience"
delete_all "/api/retirement-fund" "Retirement Fund Entries"
delete_all "/api/home-loans" "Home Loans"
delete_all "/api/dividends" "Dividends"
delete_all "/api/sold-positions" "Sold Positions"

# Transactions (need to be deleted before holdings/assets/accounts)
echo -n "  Cleaning Transactions..."
TX_IDS=$(curl -s -H "$AUTH" "$BASE_URL/api/transactions" | python3 -c "import sys,json; [print(i['id']) for i in json.load(sys.stdin)]" 2>/dev/null)
TX_COUNT=0
for id in $TX_IDS; do
  curl -s -X DELETE -H "$AUTH" "$BASE_URL/api/transactions/$id" \
    -H "Content-Type: application/json" > /dev/null
  TX_COUNT=$((TX_COUNT + 1))
done
echo " deleted $TX_COUNT records"

# Fixed Deposits
delete_all "/api/fixed-deposits" "Fixed Deposits"

# Planning deposits
delete_all "/api/planning/deposits" "Account Deposits"

# Assets and Accounts (after transactions are gone)
delete_all "/api/assets" "Assets"
delete_all "/api/accounts" "Accounts"
delete_all "/api/owners" "Owners"

echo ""
echo "========================================="
echo "  Cleanup Complete!"
echo ""
echo "  Kept: User account (admin/admin123)"
echo "         Currency rates"
echo "         Net worth config"
echo ""
echo "  You can now start adding your real data."
echo "========================================="
