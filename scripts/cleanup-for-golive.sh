#!/bin/bash
# MyFinance - Cleanup Script for Go-Live (Multi-Tenant)
#
# Removes all sample/demo finance data but KEEPS:
#   - All user accounts (admin + any users created by admin)
#   - Net worth config (per user)
#
# Since the app is multi-tenant, this removes ALL users' finance data.
# After cleanup, users start fresh with empty portfolios.
#
# Usage: bash scripts/cleanup-for-golive.sh [base-url]
#
# IMPORTANT: Backend must be running. Login as admin.

set -e

BASE_URL="${1:-http://localhost:8080}"

echo "========================================="
echo "  MyFinance - Go-Live Cleanup"
echo "  (Multi-Tenant Version)"
echo "========================================="
echo ""
echo "This will DELETE all sample finance data."
echo ""
echo "KEPT: User accounts, Net worth config"
echo "DELETED: Owners, accounts, assets, transactions,"
echo "         holdings, FDs, salary, tax, work exp,"
echo "         insurance, home loans, dividends, deposits"
echo ""
read -p "Are you sure? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

# Get auth token (must be admin)
echo ""
echo "[Auth] Logging in as admin..."
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")

if [ -z "$TOKEN" ]; then
  echo "ERROR: Could not authenticate. Is the backend running?"
  exit 1
fi

AUTH="Authorization: Bearer $TOKEN"
echo "[Auth] OK"
echo ""
echo "[Cleanup] Deleting finance data..."

# Helper: delete all items from an endpoint
delete_all() {
  local endpoint=$1
  local name=$2
  echo -n "  $name..."
  IDS=$(curl -s -H "$AUTH" "$BASE_URL$endpoint" | python3 -c "
import sys,json
try:
    data = json.load(sys.stdin)
    if isinstance(data, list):
        for i in data:
            if isinstance(i, dict) and 'id' in i:
                print(i['id'])
except: pass
" 2>/dev/null)
  COUNT=0
  for id in $IDS; do
    curl -s -X DELETE -H "$AUTH" "$BASE_URL$endpoint/$id" > /dev/null 2>&1
    COUNT=$((COUNT + 1))
  done
  echo " $COUNT deleted"
}

# Delete in order (respecting foreign keys)
delete_all "/api/salary" "Salary"
delete_all "/api/tax" "Tax Records"
delete_all "/api/work-experience" "Work Experience"
delete_all "/api/retirement-fund" "Retirement Fund"
delete_all "/api/home-loans" "Home Loans"
delete_all "/api/insurance" "Insurance"
delete_all "/api/dividends" "Dividends"
delete_all "/api/sold-positions" "Sold Positions"
delete_all "/api/planning/deposits" "Account Deposits"

# Transactions
echo -n "  Transactions..."
TX_IDS=$(curl -s -H "$AUTH" "$BASE_URL/api/transactions" | python3 -c "
import sys,json
try:
    for i in json.load(sys.stdin): print(i['id'])
except: pass
" 2>/dev/null)
TX_COUNT=0
for id in $TX_IDS; do
  curl -s -X DELETE -H "$AUTH" "$BASE_URL/api/transactions/$id" > /dev/null 2>&1
  TX_COUNT=$((TX_COUNT + 1))
done
echo " $TX_COUNT deleted"

delete_all "/api/fixed-deposits" "Fixed Deposits"
delete_all "/api/currency-rates" "Currency Rates"
delete_all "/api/assets" "Assets"
delete_all "/api/accounts" "Accounts"
delete_all "/api/owners" "Owners"

echo ""
echo "========================================="
echo "  Cleanup Complete!"
echo ""
echo "  Kept: All user accounts (admin + users)"
echo "  Kept: Net worth configuration"
echo ""
echo "  The app is ready for real data entry."
echo "  Login as a regular user to start."
echo "========================================="
