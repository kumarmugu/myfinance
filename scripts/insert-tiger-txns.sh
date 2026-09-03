#!/usr/bin/env bash
#
# One-time import of Mugu's Tiger (USD) BUY transactions via the running app's REST API.
# Going through the API (not raw SQL) means holdings, timestamps and validation are all handled
# by the service layer exactly as if entered in the UI.
#
# Target (confirmed against prod reference data):
#   user   = mugu (id 2)
#   owner  = Mugu (id 1)
#   account= Tiger USD (id 1)
#   type   = BUY, currency = USD
#
# Usage:  bash scripts/insert-tiger-txns.sh
#   Prompts for mugu's password (never echoed, never stored).
#   Optional env: BASE_URL (default http://localhost:8080)
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="mugu"
OWNER_ID=1
ACCOUNT_ID=1

command -v jq >/dev/null 2>&1 || { echo "ERROR: jq is required (brew install jq)"; exit 1; }

read -r -s -p "Password for ${USERNAME}: " PASSWORD
echo

echo "Logging in as ${USERNAME} at ${BASE_URL} ..."
TOKEN=$(curl -sf -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg u "$USERNAME" --arg p "$PASSWORD" '{username:$u,password:$p}')" \
  | jq -r '.token')
unset PASSWORD
if [ -z "${TOKEN}" ] || [ "${TOKEN}" = "null" ]; then
  echo "ERROR: login failed"; exit 1
fi
AUTH="Authorization: Bearer ${TOKEN}"
echo "Logged in."

# ── Build symbol -> assetId map from existing assets ──
declare -A ASSET_ID
while IFS=$'\t' read -r sym id; do
  [ -n "$sym" ] && ASSET_ID["$sym"]="$id"
done < <(curl -sf "${BASE_URL}/api/assets" -H "$AUTH" | jq -r '.[] | [.symbol, .id] | @tsv')

# ── Ensure an asset exists; create it if missing. Args: symbol name assetType ──
ensure_asset() {
  local sym="$1" name="$2" type="$3"
  if [ -n "${ASSET_ID[$sym]:-}" ]; then
    return
  fi
  echo "Creating asset ${sym} (${name}, ${type}) ..."
  local id
  id=$(curl -sf -X POST "${BASE_URL}/api/assets" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg n "$name" --arg s "$sym" --arg t "$type" \
          '{name:$n, symbol:$s, assetType:$t, currency:"USD", exchange:"NASDAQ"}')" \
    | jq -r '.id')
  if [ -z "$id" ] || [ "$id" = "null" ]; then
    echo "ERROR: failed to create asset ${sym}"; exit 1
  fi
  ASSET_ID["$sym"]="$id"
}

# Missing assets (existing TSLA/VOO/TQQQ/META are reused).
ensure_asset AAPL "Apple"                                       GROWTH_EQUITY
ensure_asset SPXL "Direxion Daily S&P 500 Bull 3X Shares"        LEVERAGED_ETF
ensure_asset PLTR "Palantir Technologies Inc"                   GROWTH_EQUITY
ensure_asset NVDA "NVIDIA Corp"                                 GROWTH_EQUITY
ensure_asset CRWD "CrowdStrike"                                 GROWTH_EQUITY
ensure_asset SMCI "Super Micro Computer Inc"                    GROWTH_EQUITY
ensure_asset QQQM "Invesco Nasdaq 100 ETF"                      INDEX_FUND
ensure_asset NFLX "Netflix"                                     GROWTH_EQUITY
ensure_asset FI   "FISERV INC"                                  GROWTH_EQUITY
ensure_asset VRNS "Varonis"                                     GROWTH_EQUITY
ensure_asset LRN  "Stride"                                      GROWTH_EQUITY

# ── Post one BUY transaction. Args: date(YYYY-MM-DD) symbol quantity pricePerUnit ──
post_buy() {
  local date="$1" sym="$2" qty="$3" price="$4"
  local aid="${ASSET_ID[$sym]:-}"
  if [ -z "$aid" ]; then echo "ERROR: no asset id for ${sym}"; exit 1; fi
  local body
  body=$(jq -n \
    --argjson assetId "$aid" --argjson accountId "$ACCOUNT_ID" --argjson ownerId "$OWNER_ID" \
    --argjson quantity "$qty" --argjson price "$price" --arg date "$date" \
    '{assetId:$assetId, accountId:$accountId, ownerId:$ownerId, transactionType:"BUY",
      quantity:$quantity, pricePerUnit:$price, fees:0, currency:"USD",
      transactionDate:$date, purpose:"LONG_TERM"}')
  curl -sf -X POST "${BASE_URL}/api/transactions" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "$body" >/dev/null
  echo "  + ${date}  ${sym}  qty ${qty} @ ${price}"
}

echo "Inserting transactions ..."
# date               symbol qty  price
post_buy 2022-02-24 AAPL 1   1
post_buy 2022-03-15 AAPL 2   150.08
post_buy 2022-03-08 VOO  1   387.15
post_buy 2022-03-15 TQQQ 2   21.08
post_buy 2022-03-07 SPXL 1   102.14
post_buy 2022-03-31 PLTR 10  14.22
post_buy 2022-04-06 AAPL 2   173.58
post_buy 2022-04-26 PLTR 10  11.62
post_buy 2022-05-03 SPXL 2   85.58
post_buy 2022-05-09 TQQQ 46  16.53
post_buy 2022-05-09 PLTR 25  8.25
post_buy 2022-05-12 AAPL 2   145.58
post_buy 2022-05-12 AAPL 2   143.58
post_buy 2022-05-12 TSLA 5   227.05
post_buy 2022-05-13 AAPL 3   140.38
post_buy 2022-05-21 TSLA 3   217.05
post_buy 2022-05-21 SPXL 5   70.23
post_buy 2022-05-21 VOO  2   350.58
post_buy 2022-05-24 TSLA 3   210.38
post_buy 2022-12-14 TSLA 1   162.13
post_buy 2023-04-26 TSLA 15  155.15
post_buy 2023-10-20 VOO  3   392.72
post_buy 2023-10-26 VOO  3   380.72
post_buy 2024-02-02 TSLA 5   183.44
post_buy 2024-02-05 TSLA 5   180.44
post_buy 2024-03-09 NVDA 10  85.27
post_buy 2024-03-15 VOO  10  470.22
post_buy 2024-03-22 AAPL 14  171.16
post_buy 2024-04-09 NVDA 30  83.57
post_buy 2024-04-10 VOO  5   470.44
post_buy 2024-04-16 VOO  4   463.55
post_buy 2024-04-16 TSLA 13  160.17
post_buy 2024-04-16 TQQQ 8   27.89
post_buy 2024-04-17 SPXL 12  117.18
post_buy 2024-04-18 VOO  5   459.44
post_buy 2024-04-18 TQQQ 44  26.55
post_buy 2024-04-18 NVDA 30  83.07
post_buy 2024-04-20 NVDA 30  79.07
post_buy 2024-04-20 VOO  5   455.44
post_buy 2024-06-11 TSLA 10  169.22
post_buy 2024-08-01 CRWD 10  227.22
post_buy 2024-08-28 SMCI 20  22.61
post_buy 2024-08-05 QQQM 12  175.18
post_buy 2024-11-02 SMCI 10  26.22
post_buy 2024-12-31 TSLA 4   411.12
post_buy 2024-12-31 TSLA 20  410.12
post_buy 2024-12-31 TSLA 20  409.92
post_buy 2025-02-08 TSLA 3   360.93
post_buy 2025-02-28 TSLA 13  275.17
post_buy 2025-04-15 TQQQ 60  25.04
post_buy 2025-10-22 NFLX 10  113.22
post_buy 2025-10-30 FI   30  71.58
post_buy 2025-10-29 VRNS 50  34.05
post_buy 2025-10-30 LRN  20  78.11
post_buy 2025-10-29 META 9   670.11

echo "Done. All transactions inserted."
