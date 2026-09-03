#!/usr/bin/env python3
"""One-time import of Mugu's Tiger (USD) BUY transactions via the running app's REST API.

Going through the API (not raw SQL) means holdings, timestamps and validation are handled by the
service layer exactly as if entered in the UI. Idempotency is NOT guaranteed — running twice will
create duplicate transactions, so run once.

Target (confirmed against prod reference data):
    user    = mugu (id 2)
    owner   = Mugu (id 1)
    account = Tiger USD (id 1)
    type    = BUY, currency = USD

Usage:
    python3 scripts/insert_tiger_txns.py
      Prompts for mugu's password (never echoed, never stored).
    Optional env: BASE_URL (default http://localhost:8080)
"""
import getpass
import json
import os
import urllib.request
import urllib.error

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
USERNAME = "mugu"
OWNER_ID = 1
ACCOUNT_ID = 1

# Missing assets to create (symbol, name, assetType). Existing TSLA/VOO/TQQQ/META are reused.
NEW_ASSETS = [
    ("AAPL", "Apple", "GROWTH_EQUITY"),
    ("SPXL", "Direxion Daily S&P 500 Bull 3X Shares", "LEVERAGED_ETF"),
    ("PLTR", "Palantir Technologies Inc", "GROWTH_EQUITY"),
    ("NVDA", "NVIDIA Corp", "GROWTH_EQUITY"),
    ("CRWD", "CrowdStrike", "GROWTH_EQUITY"),
    ("SMCI", "Super Micro Computer Inc", "GROWTH_EQUITY"),
    ("QQQM", "Invesco Nasdaq 100 ETF", "INDEX_FUND"),
    ("NFLX", "Netflix", "GROWTH_EQUITY"),
    ("FI", "FISERV INC", "GROWTH_EQUITY"),
    ("VRNS", "Varonis", "GROWTH_EQUITY"),
    ("LRN", "Stride", "GROWTH_EQUITY"),
]

# (date YYYY-MM-DD, symbol, quantity, pricePerUnit)
TXNS = [
    ("2022-02-24", "AAPL", 1, 1),
    ("2022-03-15", "AAPL", 2, 150.08),
    ("2022-03-08", "VOO", 1, 387.15),
    ("2022-03-15", "TQQQ", 2, 21.08),
    ("2022-03-07", "SPXL", 1, 102.14),
    ("2022-03-31", "PLTR", 10, 14.22),
    ("2022-04-06", "AAPL", 2, 173.58),
    ("2022-04-26", "PLTR", 10, 11.62),
    ("2022-05-03", "SPXL", 2, 85.58),
    ("2022-05-09", "TQQQ", 46, 16.53),
    ("2022-05-09", "PLTR", 25, 8.25),
    ("2022-05-12", "AAPL", 2, 145.58),
    ("2022-05-12", "AAPL", 2, 143.58),
    ("2022-05-12", "TSLA", 5, 227.05),
    ("2022-05-13", "AAPL", 3, 140.38),
    ("2022-05-21", "TSLA", 3, 217.05),
    ("2022-05-21", "SPXL", 5, 70.23),
    ("2022-05-21", "VOO", 2, 350.58),
    ("2022-05-24", "TSLA", 3, 210.38),
    ("2022-12-14", "TSLA", 1, 162.13),
    ("2023-04-26", "TSLA", 15, 155.15),
    ("2023-10-20", "VOO", 3, 392.72),
    ("2023-10-26", "VOO", 3, 380.72),
    ("2024-02-02", "TSLA", 5, 183.44),
    ("2024-02-05", "TSLA", 5, 180.44),
    ("2024-03-09", "NVDA", 10, 85.27),
    ("2024-03-15", "VOO", 10, 470.22),
    ("2024-03-22", "AAPL", 14, 171.16),
    ("2024-04-09", "NVDA", 30, 83.57),
    ("2024-04-10", "VOO", 5, 470.44),
    ("2024-04-16", "VOO", 4, 463.55),
    ("2024-04-16", "TSLA", 13, 160.17),
    ("2024-04-16", "TQQQ", 8, 27.89),
    ("2024-04-17", "SPXL", 12, 117.18),
    ("2024-04-18", "VOO", 5, 459.44),
    ("2024-04-18", "TQQQ", 44, 26.55),
    ("2024-04-18", "NVDA", 30, 83.07),
    ("2024-04-20", "NVDA", 30, 79.07),
    ("2024-04-20", "VOO", 5, 455.44),
    ("2024-06-11", "TSLA", 10, 169.22),
    ("2024-08-01", "CRWD", 10, 227.22),
    ("2024-08-28", "SMCI", 20, 22.61),
    ("2024-08-05", "QQQM", 12, 175.18),
    ("2024-11-02", "SMCI", 10, 26.22),
    ("2024-12-31", "TSLA", 4, 411.12),
    ("2024-12-31", "TSLA", 20, 410.12),
    ("2024-12-31", "TSLA", 20, 409.92),
    ("2025-02-08", "TSLA", 3, 360.93),
    ("2025-02-28", "TSLA", 13, 275.17),
    ("2025-04-15", "TQQQ", 60, 25.04),
    ("2025-10-22", "NFLX", 10, 113.22),
    ("2025-10-30", "FI", 30, 71.58),
    ("2025-10-29", "VRNS", 50, 34.05),
    ("2025-10-30", "LRN", 20, 78.11),
    ("2025-10-29", "META", 9, 670.11),
]


def call(method, path, token=None, body=None):
    url = f"{BASE_URL}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        raise SystemExit(f"ERROR {e.code} on {method} {path}: {detail}")
    except urllib.error.URLError as e:
        raise SystemExit(f"ERROR connecting to {url}: {e}")


def main():
    password = getpass.getpass(f"Password for {USERNAME}: ")
    print(f"Logging in as {USERNAME} at {BASE_URL} ...")
    auth = call("POST", "/api/auth/login", body={"username": USERNAME, "password": password})
    token = auth.get("token")
    if not token:
        raise SystemExit("ERROR: login failed (no token)")
    print("Logged in.")

    # Build symbol -> id map from existing assets.
    assets = call("GET", "/api/assets", token=token)
    asset_id = {a["symbol"]: a["id"] for a in assets}

    # Create missing assets.
    for sym, name, atype in NEW_ASSETS:
        if sym in asset_id:
            continue
        print(f"Creating asset {sym} ({name}, {atype}) ...")
        created = call("POST", "/api/assets", token=token, body={
            "name": name, "symbol": sym, "assetType": atype,
            "currency": "USD", "exchange": "NASDAQ",
        })
        asset_id[sym] = created["id"]

    # Insert transactions.
    print("Inserting transactions ...")
    count = 0
    for date, sym, qty, price in TXNS:
        aid = asset_id.get(sym)
        if not aid:
            raise SystemExit(f"ERROR: no asset id for {sym}")
        call("POST", "/api/transactions", token=token, body={
            "assetId": aid, "accountId": ACCOUNT_ID, "ownerId": OWNER_ID,
            "transactionType": "BUY", "quantity": qty, "pricePerUnit": price,
            "fees": 0, "currency": "USD", "transactionDate": date, "purpose": "LONG_TERM",
        })
        count += 1
        print(f"  + {date}  {sym}  qty {qty} @ {price}")

    print(f"Done. Inserted {count} transactions.")


if __name__ == "__main__":
    main()
