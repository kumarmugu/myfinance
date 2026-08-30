# MyFinance - Design Document

## 1. Database Schema

### 1.1 Core Tables

#### `app_users`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Primary key |
| username | VARCHAR(50) | NOT NULL, UNIQUE | Login username |
| email | VARCHAR(100) | NOT NULL, UNIQUE | Email address |
| password | VARCHAR(255) | NOT NULL | BCrypt encoded |
| display_name | VARCHAR(100) | | Display name |
| role | VARCHAR(10) | DEFAULT 'USER' | USER or ADMIN |
| is_active | BOOLEAN | DEFAULT true | Account active |
| sl_fd_enabled | BOOLEAN | DEFAULT false | Sri Lanka FD module toggle |
| enabled_features | VARCHAR(500) | | CSV of enabled feature keys; null/empty = all |
| base_currency | VARCHAR(10) | | Per-user base/reporting currency; null = SGD |
| display_currencies | VARCHAR(200) | | CSV of UI display-currency options; null = SGD,USD |
| reset_token | VARCHAR(255) | | Password reset token |
| reset_token_expiry | TIMESTAMP | | Token expiry time |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | | |

> **Currency model.** `base_currency` and `display_currencies` are *reporting/display* concepts only. Every financial record keeps its own **original currency + amount** as the source of truth; SGD/base and other display currencies are derived on the fly via the user's FX rates (see §Currency Handling). Both columns are nullable and backward compatible (null → SGD / `SGD,USD`).

#### `audit_logs`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Primary key |
| user_id | BIGINT | INDEX | Who performed action |
| username | VARCHAR(50) | | Username for display |
| action | VARCHAR(20) | NOT NULL, INDEX | CREATE, UPDATE, DELETE |
| entity | VARCHAR(50) | NOT NULL, INDEX | Entity type (Account, Asset...) |
| entity_id | BIGINT | | Affected record ID |
| details | VARCHAR(1000) | | Additional context |
| timestamp | TIMESTAMP | NOT NULL, INDEX | When it happened |

#### `owners`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Primary key |
| name | VARCHAR(100) | NOT NULL | Display name |
| relationship | VARCHAR(20) | NOT NULL | SELF, SPOUSE |
| is_active | BOOLEAN | DEFAULT true | Active flag |
| created_at | TIMESTAMP | NOT NULL | Record creation time |

#### `accounts`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Primary key |
| name | VARCHAR(100) | NOT NULL | Account name (Tiger, Saxo, DBS, etc.) |
| account_type | VARCHAR(20) | NOT NULL | BROKER, BANK, CRYPTO_EXCHANGE |
| owner_id | BIGINT | FK → owners.id | Account owner |
| currency | VARCHAR(3) | DEFAULT 'SGD' | Primary currency |
| description | VARCHAR(255) | | Optional description |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | | |

#### `assets`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Primary key |
| name | VARCHAR(200) | NOT NULL | Full name |
| symbol | VARCHAR(20) | NOT NULL, UNIQUE | Ticker symbol |
| asset_type | VARCHAR(30) | NOT NULL | INDEX_FUND, GROWTH_EQUITY, etc. |
| current_price | DECIMAL(18,6) | | Latest known price |
| currency | VARCHAR(3) | DEFAULT 'USD' | Price currency |
| exchange | VARCHAR(50) | | Exchange/market |
| description | VARCHAR(255) | | |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | | |

#### `currency_rates`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| from_currency | VARCHAR(3) | NOT NULL | Source currency |
| to_currency | VARCHAR(3) | NOT NULL | Target currency |
| rate | DECIMAL(12,6) | NOT NULL | Conversion rate |
| effective_date | DATE | NOT NULL | Rate effective date |
| updated_at | TIMESTAMP | | |

---

### 1.2 Investment Tables

#### `transactions`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| asset_id | BIGINT | FK → assets.id, NOT NULL | Asset traded |
| account_id | BIGINT | FK → accounts.id, NOT NULL | Broker account |
| owner_id | BIGINT | FK → owners.id, NOT NULL | Who owns this |
| transaction_type | VARCHAR(15) | NOT NULL | BUY, SELL |
| quantity | DECIMAL(18,8) | NOT NULL | Units traded |
| price_per_unit | DECIMAL(18,6) | NOT NULL | Price at transaction |
| total_amount | DECIMAL(18,2) | NOT NULL | Total value |
| fees | DECIMAL(18,2) | DEFAULT 0 | Transaction fees |
| currency | VARCHAR(3) | NOT NULL | Transaction currency |
| transaction_date | DATE | NOT NULL | When it happened |
| notes | VARCHAR(500) | | |
| created_at | TIMESTAMP | NOT NULL | |

#### `holdings`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| asset_id | BIGINT | FK → assets.id, NOT NULL | |
| account_id | BIGINT | FK → accounts.id, NOT NULL | |
| owner_id | BIGINT | FK → owners.id, NOT NULL | |
| quantity | DECIMAL(18,8) | NOT NULL | Current units held |
| average_buy_price | DECIMAL(18,6) | NOT NULL | Weighted average cost |
| invested_amount | DECIMAL(18,2) | NOT NULL | Total cost basis |
| currency | VARCHAR(3) | NOT NULL | |
| updated_at | TIMESTAMP | | |
| UNIQUE | | (asset_id, account_id, owner_id) | No duplicate holdings |

#### `sold_positions`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| asset_id | BIGINT | FK → assets.id | |
| account_id | BIGINT | FK → accounts.id | |
| owner_id | BIGINT | FK → owners.id | |
| quantity | DECIMAL(18,8) | NOT NULL | Units sold |
| buy_price | DECIMAL(18,6) | NOT NULL | Original purchase price |
| sell_price | DECIMAL(18,6) | NOT NULL | Price sold at |
| invested_amount | DECIMAL(18,2) | NOT NULL | Cost basis |
| sold_amount | DECIMAL(18,2) | NOT NULL | Proceeds |
| profit | DECIMAL(18,2) | NOT NULL | Realized gain/loss |
| profit_percentage | DECIMAL(8,2) | | % gain/loss |
| currency | VARCHAR(3) | NOT NULL | |
| invested_date | DATE | NOT NULL | Original buy date |
| sold_date | DATE | NOT NULL | Sell date |
| holding_period | VARCHAR(50) | | Human-readable period |
| is_short_term | BOOLEAN | DEFAULT false | Short-term trade flag |
| notes | VARCHAR(500) | | |

#### `dividends`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| asset_id | BIGINT | FK → assets.id | |
| account_id | BIGINT | FK → accounts.id | |
| owner_id | BIGINT | FK → owners.id | |
| amount | DECIMAL(18,2) | NOT NULL | Dividend received |
| currency | VARCHAR(3) | NOT NULL | |
| received_date | DATE | NOT NULL | Payment date |
| year | INT | NOT NULL | Tax year |
| quarter | VARCHAR(10) | | Q1, Q2, Q3, Q4 |
| notes | VARCHAR(255) | | |

#### `account_deposits`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| account_id | BIGINT | FK → accounts.id | |
| amount | DECIMAL(18,2) | NOT NULL | Deposit/withdrawal amount |
| deposit_type | VARCHAR(15) | NOT NULL | DEPOSIT or WITHDRAWAL |
| currency | VARCHAR(3) | NOT NULL | |
| deposit_date | DATE | NOT NULL | |
| notes | VARCHAR(255) | | |

---

### 1.3 Fixed Deposit Tables

#### `banks`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(100) | NOT NULL | Full bank name |
| short_name | VARCHAR(20) | NOT NULL | Abbreviation (NSB, BOC, HNB) |
| country | VARCHAR(50) | DEFAULT 'Sri Lanka' | |

#### `fd_holders`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(100) | NOT NULL | Holder name |
| relationship | VARCHAR(30) | | Family relationship |
| is_senior_citizen | BOOLEAN | DEFAULT false | Senior citizen rate eligibility |
| notes | VARCHAR(255) | | |

#### `fixed_deposits`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| holder_id | BIGINT | FK → fd_holders.id | Primary holder |
| joint_holder_id | BIGINT | FK → fd_holders.id | Joint holder (if any) |
| bank_id | BIGINT | FK → banks.id | Bank |
| account_number | VARCHAR(50) | | FD account/certificate number |
| principal_amount | DECIMAL(18,2) | NOT NULL | Deposit amount (LKR) |
| interest_rate | DECIMAL(5,2) | NOT NULL | Annual interest % |
| start_date | DATE | NOT NULL | Deposit start date |
| maturity_date | DATE | NOT NULL | Maturity date |
| period | VARCHAR(30) | | Duration description |
| branch | VARCHAR(100) | | Bank branch |
| category | VARCHAR(20) | | NORMAL, SENIOR_CITIZEN |
| status | VARCHAR(20) | DEFAULT 'ACTIVE' | ACTIVE, MATURED, RENEWED, REQUIRES_UPDATE |
| expected_interest | DECIMAL(18,2) | | Calculated interest amount |
| beneficiary | VARCHAR(30) | | Who benefits (parent indicator indicator) |
| purpose | VARCHAR(100) | | Purpose (deed, car, etc.) |
| notes | VARCHAR(500) | | |
| requires_update | BOOLEAN | DEFAULT false | Flag for needing verification |
| created_at | TIMESTAMP | | |
| updated_at | TIMESTAMP | | |

---

### 1.4 Planning Tables

#### `net_worth_snapshots`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| owner_id | BIGINT | FK → owners.id | |
| snapshot_date | DATE | NOT NULL | Snapshot date |
| total_index_fund | DECIMAL(18,2) | NOT NULL | |
| total_mutual_fund | DECIMAL(18,2) | NOT NULL | |
| total_growth_equity | DECIMAL(18,2) | NOT NULL | |
| total_dividend_equity | DECIMAL(18,2) | NOT NULL | |
| total_leveraged_etf | DECIMAL(18,2) | NOT NULL | |
| total_money_market | DECIMAL(18,2) | NOT NULL | |
| total_fixed_deposit | DECIMAL(18,2) | NOT NULL | |
| total_savings | DECIMAL(18,2) | NOT NULL | |
| total_crypto | DECIMAL(18,2) | NOT NULL | |
| total_net_worth | DECIMAL(18,2) | NOT NULL | |
| currency | VARCHAR(3) | DEFAULT 'SGD' | |
| created_at | TIMESTAMP | | |

#### `allocation_targets`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| owner_id | BIGINT | FK → owners.id | |
| asset_type | VARCHAR(30) | NOT NULL | |
| target_percentage | DECIMAL(5,2) | NOT NULL | Target % |
| target_amount | DECIMAL(18,2) | | Target $ amount |
| notes | VARCHAR(255) | | |

#### `srs_plans`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| owner_id | BIGINT | FK → owners.id | |
| year | INT | NOT NULL | Plan year |
| age | INT | NOT NULL | Age in that year |
| expected_contribution | DECIMAL(18,2) | | Planned contribution |
| actual_contribution | DECIMAL(18,2) | | Actual contributed |
| accumulated_value | DECIMAL(18,2) | | Running total with growth |
| expected_growth_rate | DECIMAL(5,2) | | Growth % assumption |
| tax_relief | DECIMAL(18,2) | | Tax relief gained |
| notes | VARCHAR(255) | | |

#### `account_net_worth_history`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| account_id | BIGINT | FK → accounts.id | |
| owner_id | BIGINT | FK → owners.id | |
| year | INT | NOT NULL | Calendar year |
| value | DECIMAL(18,2) | NOT NULL | Account value at year end |
| currency | VARCHAR(3) | NOT NULL | |

---

## 2. Detailed API Specifications

### 2.1 Fixed Deposit APIs

#### `GET /api/fixed-deposits`
**Query Parameters:**
- `holderId` (optional) - Filter by holder
- `bankId` (optional) - Filter by bank
- `status` (optional) - Filter by status (ACTIVE, MATURED, etc.)
- `beneficiary` (optional) - Filter by beneficiary type

**Response:**
```json
[
  {
    "id": 1,
    "holder": { "id": 1, "name": "Parent A", "isSeniorCitizen": true },
    "jointHolder": { "id": 2, "name": "Parent B" },
    "bank": { "id": 1, "name": "National Savings Bank", "shortName": "NSB" },
    "accountNumber": "FD-ACC-001",
    "principalAmount": 575000.00,
    "interestRate": 8.0,
    "startDate": "2025-01-11",
    "maturityDate": "2026-01-11",
    "period": "12 Months",
    "branch": "Chavakachcheri",
    "category": "SENIOR_CITIZEN",
    "status": "ACTIVE",
    "expectedInterest": 46000.00,
    "beneficiary": "PARENT_B",
    "requiresUpdate": false,
    "daysToMaturity": 145,
    "notes": null
  }
]
```

#### `GET /api/fixed-deposits/summary`
**Response:**
```json
{
  "totalPrincipal": 25000000.00,
  "totalExpectedInterest": 1800000.00,
  "totalActiveFDs": 41,
  "byBank": [
    { "bank": "BOC", "count": 20, "amount": 19103684.77, "percentage": 58 },
    { "bank": "NSB", "count": 7, "amount": 6727062.53, "percentage": 20 },
    { "bank": "Seylan", "count": 9, "amount": 4670506.05, "percentage": 14 }
  ],
  "byBeneficiary": [
    { "beneficiary": "Parent B", "count": 23, "amount": 15000000.00, "percentage": 61 },
    { "beneficiary": "Parent A", "count": 18, "amount": 10000000.00, "percentage": 39 }
  ],
  "maturingWithin30Days": 2,
  "maturingWithin90Days": 5,
  "requiresUpdate": 4
}
```

#### `GET /api/fixed-deposits/maturing`
**Query Parameters:**
- `days` (default: 30) - Days to look ahead

**Response:**
```json
[
  {
    "id": 5,
    "holder": "Sibling1/Parent A",
    "bank": "NSB",
    "principalAmount": 781860.77,
    "maturityDate": "2025-07-15",
    "daysToMaturity": 5,
    "interestRate": 6.85,
    "expectedInterest": 53557.46,
    "action": "NEEDS_RENEWAL"
  }
]
```

#### `GET /api/fixed-deposits/interest-report`
**Query Parameters:**
- `year` (optional) - Filter by year

**Response:**
```json
{
  "totalInterestIncome": 1800000.00,
  "byHolder": [
    { "holder": "Parent B", "interest": 1592251.12 },
    { "holder": "Parent A", "interest": 842473.97 }
  ],
  "byBank": [
    { "bank": "BOC", "interest": 1200000.00 },
    { "bank": "NSB", "interest": 600000.00 }
  ],
  "monthlyProjection": [
    { "month": "2025-07", "maturing": 3, "interestDue": 172000.00 }
  ]
}
```

---

### 2.2 Planning APIs

#### `GET /api/planning/allocation`
**Response:**
```json
{
  "current": [
    { "type": "INDEX_FUND", "amount": 324385.60, "percentage": 35.76 },
    { "type": "GROWTH_EQUITY", "amount": 200110.21, "percentage": 22.06 },
    { "type": "SAVINGS", "amount": 218847.00, "percentage": 24.13 },
    { "type": "MONEY_MARKET", "amount": 63725.55, "percentage": 7.03 },
    { "type": "FIXED_DEPOSIT", "amount": 24093.55, "percentage": 2.66 },
    { "type": "DIVIDEND_EQUITY", "amount": 23566.78, "percentage": 2.60 },
    { "type": "MUTUAL_FUND", "amount": 19166.70, "percentage": 2.11 },
    { "type": "LEVERAGED_ETF", "amount": 17947.99, "percentage": 1.98 },
    { "type": "CRYPTO", "amount": 15260.18, "percentage": 1.68 }
  ],
  "target": [
    { "type": "INDEX_FUND", "targetPercentage": 37, "targetAmount": 370000 },
    { "type": "GROWTH_EQUITY", "targetPercentage": 23, "targetAmount": 230000 },
    { "type": "DIVIDEND_EQUITY", "targetPercentage": 10, "targetAmount": 100000 },
    { "type": "MONEY_MARKET", "targetPercentage": 10, "targetAmount": 100000 },
    { "type": "LEVERAGED_ETF", "targetPercentage": 5, "targetAmount": 50000 },
    { "type": "FIXED_DEPOSIT", "targetPercentage": 5, "targetAmount": 50000 },
    { "type": "CRYPTO", "targetPercentage": 5, "targetAmount": 50000 },
    { "type": "SAVINGS", "targetPercentage": 3, "targetAmount": 30000 },
    { "type": "MUTUAL_FUND", "targetPercentage": 2, "targetAmount": 20000 }
  ],
  "balanceToInvest": [
    { "type": "INDEX_FUND", "amount": 45614.40 },
    { "type": "DIVIDEND_EQUITY", "amount": 76433.22 },
    { "type": "LEVERAGED_ETF", "amount": 32052.01 },
    { "type": "SAVINGS", "amount": -188847.00 }
  ],
  "totalPortfolioValue": 907103.56,
  "targetTotal": 1000000
}
```

#### `GET /api/planning/srs`
**Response:**
```json
{
  "currentAge": 39,
  "plan": [
    {
      "year": 2025,
      "age": 39,
      "contribution": 35700,
      "accumulated": 37485,
      "taxRelief": 6962,
      "growthRate": 8
    },
    {
      "year": 2026,
      "age": 40,
      "contribution": 15300,
      "accumulated": 52785,
      "taxRelief": 2984,
      "growthRate": 8
    }
  ],
  "projectedValueAt62": 500008,
  "totalContributions": 86700,
  "totalTaxRelief": 97515,
  "withdrawalStrategy": {
    "startAge": 63,
    "annualWithdrawal": 60000,
    "taxPerWithdrawal": 550,
    "yearsOfWithdrawal": 10
  }
}
```

#### `GET /api/planning/net-worth/history`
**Response:**
```json
{
  "history": [
    {
      "year": 2021,
      "total": 392547.60,
      "byAccount": {
        "Tiger": 101025.60,
        "Saxo": 103312.00,
        "OCBC": 113000.00
      }
    },
    {
      "year": 2022,
      "total": 658103.90,
      "growth": 265556.30,
      "growthPercentage": 67.65
    },
    {
      "year": 2023,
      "total": 910269.28,
      "growth": 252165.38,
      "growthPercentage": 38.32
    }
  ]
}
```

---

### 2.3 Reporting APIs

#### `GET /api/reports/dashboard`
**Response:**
```json
{
  "netWorth": {
    "total": 910269.28,
    "currency": "SGD",
    "changeFromLastYear": 252165.38,
    "changePercentage": 38.32
  },
  "investments": {
    "totalInvested": 750000,
    "currentValue": 910269.28,
    "totalGainLoss": 160269.28,
    "gainLossPercentage": 21.37
  },
  "topPerformers": [
    { "symbol": "PLTR", "gainPercentage": 1166.44 },
    { "symbol": "META", "gainPercentage": 171.40 },
    { "symbol": "NVDA", "gainPercentage": 138.64 }
  ],
  "worstPerformers": [
    { "symbol": "RIVN", "gainPercentage": -79.97 },
    { "symbol": "FRSH", "gainPercentage": -67.24 },
    { "symbol": "ARKK", "gainPercentage": -37.77 }
  ],
  "recentDividends": {
    "total2025": 2050.98,
    "lastQuarter": "Q4-2025",
    "lastQuarterAmount": 557.99
  },
  "fixedDeposits": {
    "totalPrincipal": 25000000.00,
    "currency": "LKR",
    "maturingSoon": 3
  }
}
```

#### `GET /api/reports/performance`
**Query Parameters:**
- `groupBy` - `type`, `broker`, `asset`
- `ownerId` (optional)
- `year` (optional)

**Response (groupBy=broker):**
```json
[
  {
    "broker": "Tiger",
    "invested": 100000.00,
    "currentValue": 150000.00,
    "profit": 50000.00,
    "profitPercentage": 67.63,
    "currency": "USD"
  },
  {
    "broker": "Saxo",
    "invested": 80000.00,
    "currentValue": 120000.00,
    "profit": 34550.82,
    "profitPercentage": 32.16,
    "currency": "SGD"
  },
  {
    "broker": "IBKR",
    "invested": 110000.00,
    "currentValue": 130000.00,
    "profit": 5634.78,
    "profitPercentage": 3.26,
    "currency": "SGD"
  }
]
```

#### `GET /api/reports/dividends/summary`
**Query Parameters:**
- `year` (optional)
- `broker` (optional)

**Response:**
```json
{
  "totalDividends": 2050.98,
  "totalCustodyFees": 0,
  "netDividendIncome": 2050.98,
  "byYear": [
    { "year": 2020, "dividends": 45.94, "fees": 1.11, "net": 44.83 },
    { "year": 2021, "dividends": 296.16, "fees": 42.89, "net": 253.27 },
    { "year": 2022, "dividends": 430.23, "fees": 108.24, "net": 321.99 },
    { "year": 2023, "dividends": 521.25, "fees": 130.07, "net": 391.18 },
    { "year": 2024, "dividends": 757.40, "fees": 50.25, "net": 707.15 },
    { "year": 2025, "dividends": 2050.98, "fees": 0, "net": 2050.98 }
  ],
  "byInstrument": [
    { "instrument": "VOO", "year": 2025, "total": 237.38 },
    { "instrument": "VGT", "year": 2025, "total": 116.36 },
    { "instrument": "QQQ", "year": 2025, "total": 124.07 }
  ],
  "brokerBreakdown": [
    { "broker": "Saxo", "total": 557.99 },
    { "broker": "IBKR", "total": 1280.26 },
    { "broker": "Tiger", "total": 250.22 }
  ]
}
```

---

## 3. UI/UX Design Specifications

### 3.1 Design System

**Color Palette:**
| Usage | Color | Hex |
|-------|-------|-----|
| Primary | Indigo | #6366f1 |
| Success/Gain | Green | #10b981 |
| Danger/Loss | Red | #ef4444 |
| Warning | Amber | #f59e0b |
| Info | Cyan | #06b6d4 |
| Neutral | Slate | #64748b |
| Background | Slate 50 | #f8fafc |
| Card | White | #ffffff |

**Typography:**
- Headings: Inter/System font, Bold
- Body: Inter/System font, Regular
- Numbers: Tabular numerals for alignment

**Component Patterns:**
- Cards with rounded-xl, border-slate-200, shadow-sm
- Tables with sticky headers, alternating row hover
- Forms with labeled inputs, validation feedback
- Charts using Recharts with consistent color scheme

### 3.2 Navigation Structure

```
┌─────────────────────────────────────────────────────────────┐
│ SIDEBAR                                                      │
├─────────────────────────────────────────────────────────────┤
│ 🏠 Dashboard                                                │
│ ─────────────────────                                        │
│ 📊 INVESTMENTS                                               │
│   📈 Portfolio                                               │
│   🔄 Transactions                                            │
│   💰 Dividends                                               │
│ ─────────────────────                                        │
│ 🏦 FIXED DEPOSITS                                            │
│   📋 All Deposits                                            │
│   📊 FD Reports                                              │
│ ─────────────────────                                        │
│ 📐 PLANNING                                                  │
│   🎯 Allocation                                              │
│   🏖️ SRS Plan                                                │
│   📈 Projections                                             │
│ ─────────────────────                                        │
│ 📊 REPORTS                                                   │
│   📈 Net Worth                                               │
│   📉 Performance                                             │
│   📅 Year-over-Year                                          │
│ ─────────────────────                                        │
│ ⚙️ SETTINGS                                                  │
│   🏢 Accounts                                                │
│   📦 Assets                                                  │
│   📄 Documentation                                           │
└─────────────────────────────────────────────────────────────┘
```

---

### 3.3 Page Designs

#### 3.3.1 Dashboard Page

```
┌─────────────────────────────────────────────────────────────┐
│ Dashboard                             [Owner: All ▾]         │
├─────────────────────────────────────────────────────────────┤
│ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌───────────┐ │
│ │ Net Worth  │ │ Invested   │ │ Gain/Loss  │ │ Dividends │ │
│ │ S$XXX,XXX  │ │ S$XXX,XXX  │ │ +S$XX,XXX │ │ S$X,XXX   │ │
│ │            │ │            │ │ +21.37%    │ │ YTD 2025  │ │
│ └────────────┘ └────────────┘ └────────────┘ └───────────┘ │
│                                                              │
│ ┌─────────────────────────────────┐ ┌────────────────────┐  │
│ │ Net Worth History (Area Chart)   │ │ Asset Allocation   │  │
│ │ [2021] [2022] [2023] [2024] [25]│ │ (Donut Chart)      │  │
│ │ ╱──────────╲                     │ │                    │  │
│ │╱            ╲──────              │ │  ┌──┐ Index 37%    │  │
│ │                                  │ │  │  │ Growth 22%   │  │
│ │                                  │ │  └──┘ Savings 24%  │  │
│ └─────────────────────────────────┘ └────────────────────┘  │
│                                                              │
│ ┌─────────────────────────────────┐ ┌────────────────────┐  │
│ │ Top Performers                   │ │ Account Values     │  │
│ │ PLTR  +1166%  ████████████████  │ │ Tiger    $249,461  │  │
│ │ META  +171%   ████████          │ │ IBKR     $169,161  │  │
│ │ NVDA  +138%   ███████           │ │ Saxo     $154,551  │  │
│ │ TQQQ  +114%   ██████            │ │ OCBC     $108,493  │  │
│ └─────────────────────────────────┘ └────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.2 Fixed Deposits Page

```
┌─────────────────────────────────────────────────────────────┐
│ Fixed Deposits                [+ New FD] [Export]             │
├─────────────────────────────────────────────────────────────┤
│ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌───────────┐ │
│ │ Total FDs  │ │ Principal  │ │ Interest   │ │ Maturing  │ │
│ │ 41         │ │ ₨32.9M    │ │ ₨2.43M    │ │ 3 soon    │ │
│ └────────────┘ └────────────┘ └────────────┘ └───────────┘ │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Filters: [Bank ▾] [Holder ▾] [Status ▾] [Search...]    │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ # │Holder       │Bank │Amount    │Rate│Start   │Maturity│ │
│ │───┼─────────────┼─────┼──────────┼────┼────────┼────────│ │
│ │ 1 │Parent A│NSB  │575,000   │8%  │11/01/25│11/01/26│ │
│ │ 2 │Parent A│Comm │194,000   │8%  │28/01/25│28/01/26│ │
│ │ 3 │Parent B   │HNB  │1,030,000 │8%  │22/05/25│22/05/26│ │
│ │...│             │     │          │    │        │        │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌────────────────────────────┐ ┌──────────────────────────┐ │
│ │ By Bank (Pie Chart)        │ │ Maturity Calendar        │ │
│ │ BOC 58%  NSB 20%           │ │ Jul: ██ 3 FDs            │ │
│ │ Seylan 14%  Others 8%     │ │ Aug: █ 1 FD              │ │
│ └────────────────────────────┘ └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.3 Allocation Planning Page

```
┌─────────────────────────────────────────────────────────────┐
│ Target Allocation                    Target: S$1,000,000     │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Asset Type        │ Current  │ Target │ Actual% │ Gap   │ │
│ │───────────────────┼──────────┼────────┼─────────┼───────│ │
│ │ Index Fund        │ 324,386  │370,000 │ 35.76%  │-45,614│ │
│ │ Growth Equity     │ 200,110  │230,000 │ 22.06%  │-29,890│ │
│ │ Dividend Equity   │ 23,567   │100,000 │ 2.60%   │-76,433│ │
│ │ Money Market      │ 63,726   │100,000 │ 7.03%   │-36,274│ │
│ │ Leveraged ETF     │ 17,948   │ 50,000 │ 1.98%   │-32,052│ │
│ │ Fixed Deposit     │ 24,094   │ 50,000 │ 2.66%   │-25,906│ │
│ │ Crypto            │ 15,260   │ 50,000 │ 1.68%   │-34,740│ │
│ │ Savings           │ 218,847  │ 30,000 │ 24.13%  │+188,847│ │
│ │ Mutual Fund       │ 19,167   │ 20,000 │ 2.11%   │-833   │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │        Actual vs Target (Stacked Bar Chart)              │ │
│ │  Index  ████████████████████░░░  35.76% / 37%           │ │
│ │  Growth ██████████████░░░░░░░░░  22.06% / 23%           │ │
│ │  Divid  ██░░░░░░░░░░░░░░░░░░░░   2.60% / 10%           │ │
│ │  Savings████████████████████████  24.13% / 3% (OVER)    │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.4 Portfolio Page (Enhanced)

```
┌─────────────────────────────────────────────────────────────┐
│ Portfolio      [Active │ Sold │ Short-Term]  [Owner ▾]       │
├─────────────────────────────────────────────────────────────┤
│ Tabs: [By Holding] [By Broker] [By Type]                     │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Asset  │Account│Units │Avg Price│Current│Invested│P&L   │ │
│ │────────┼───────┼──────┼─────────┼───────┼────────┼──────│ │
│ │ VOO    │Tiger  │48    │$442.15  │$629.95│$21,223 │+$9,055│
│ │ VOO    │Saxo   │37    │$547.22  │$808.59│$20,247 │+$9,671│
│ │ VOO    │IBKR   │10    │$500.04  │$629.79│$5,000  │+$1,298│
│ │ TSLA   │Tiger  │137   │$275.44  │$453.34│$37,735 │+$24,373│
│ │ TSLA   │Saxo   │21    │$416.88  │$583.14│$8,754  │+$3,492│
│ │ SPYL   │IBKR   │3697  │$15.85   │$16.95 │$58,601 │+$4,039│
│ │ ...    │       │      │         │       │        │       │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ [Sold Positions Tab]                                         │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Asset │Account│Units│Buy Price│Sell Price│Profit│Hold Time│ │
│ │───────┼───────┼─────┼─────────┼──────────┼──────┼────────│ │
│ │ NVDA  │Tiger  │30   │$95.57   │$135.00   │$1,181│3 Months│ │
│ │ TSLA  │Tiger  │6    │$263.69  │$300.00   │$217  │2Y 7M   │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.5 Net Worth History Page

```
┌─────────────────────────────────────────────────────────────┐
│ Net Worth History                    [Take Snapshot]          │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Year-End Net Worth (Stacked Area Chart)                  │ │
│ │                                                          │ │
│ │ S$1M ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ── ╱           │ │
│ │                                           ╱              │ │
│ │ S$750K ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╱──              │ │
│ │                                    ╱                     │ │
│ │ S$500K ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╱──                        │ │
│ │                            ╱                             │ │
│ │ S$250K ─ ─ ─ ─ ─ ─ ─ ╱──                              │ │
│ │         2021    2022    2023    2024    2025              │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Year │ Net Worth  │ Growth    │ Growth %  │ Notes        │ │
│ │──────┼────────────┼───────────┼───────────┼──────────────│ │
│ │ 2021 │ S$XXX,XXX  │ -         │ -         │ Starting     │ │
│ │ 2022 │ S$XXX,XXX  │ +S$XX,XXX│ +XX.XX%   │              │ │
│ │ 2023 │ S$XXX,XXX  │ +S$XX,XXX│ +XX.XX%   │              │ │
│ │ 2024 │ S$XXX,XXX  │ -S$X,XXX  │ -X.XX%    │ Market adj   │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Account Breakdown by Year (Grouped Bar Chart)            │ │
│ │ Shows each account's value across years                  │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Fixed Deposit Module - Detailed Design

### 4.1 FD Categories (from Excel analysis)

| Category | Owner Groups | Description |
|----------|-------------|-------------|
| **Parents** | Parent A, Parent B | Parents' FDs |
| **Self** | Self/Parent B, Self/Parent A | User's FDs (joint with parents) |
| **Sibling 1** | Sibling1/Parent B, Sibling1/Parent A, Sibling 1 | Sibling's FDs |
| **Sibling 2** | Sibling2/Parent B, Sibling2/Parent A | Another sibling's FDs |
| **Car** | Self/Parent A, Self/Parent B, Sibling2/Parent A | Purpose-specific FDs |

### 4.2 Banks Supported

| Bank | Short Name | Has Senior Rate |
|------|-----------|-----------------|
| National Savings Bank | NSB | Yes |
| Bank of Ceylon | BOC | Yes |
| Commercial Bank | Commercial | Yes |
| Seylan Bank | Seylan | No |
| People's Bank | Peoples | No |
| Hatton National Bank | HNB | Yes |
| Sampath Bank | Sampath | No |

### 4.3 FD Status Workflow

```
                    ┌──────────┐
                    │  ACTIVE  │
                    └─────┬────┘
                          │
              ┌───────────┼───────────┐
              ▼           ▼           ▼
     ┌────────────┐ ┌──────────┐ ┌────────────────┐
     │  MATURED   │ │ RENEWED  │ │ REQUIRES_UPDATE│
     └─────┬──────┘ └──────────┘ └────────────────┘
           │
           ▼
     ┌──────────┐
     │  CLOSED  │
     └──────────┘
```

### 4.4 FD Reports

1. **Summary Dashboard** - Total by bank, by holder, by category
2. **Maturity Calendar** - Visual timeline of upcoming maturities
3. **Interest Income Report** - Expected and realized interest
4. **Beneficiary Report** - Split between parent accounts
5. **Renewal Alert** - FDs needing action (flagged with `requires_update`)

---

## 5. Financial Planning Module - Detailed Design

### 5.1 Net Worth Tracking

The net worth is tracked across these dimensions:

**By Account:**
- DBS, OCBC, CIMB (Banks/Savings)
- Tiger, Saxo, Poems, IBKR, Moomoo (Brokers)
- Coinhako, Crypto.com (Crypto exchanges)
- SL-Fixed (Sri Lanka Fixed Deposits)
- Loan to friends

**By Asset Type (9 categories):**
- Index Fund, Mutual Fund, Growth Equities, Dividend Equities
- 3x Equities (Leveraged ETFs), Money Market
- Fixed Deposit, Savings, Crypto

### 5.2 Target Allocation Model

Users define target allocation as:
- Target percentage (must sum to 100%)
- Target dollar amount (based on total portfolio target)
- System calculates gap between current and target

### 5.3 SRS (Supplementary Retirement Scheme) Planning

**Key Parameters:**
- Annual contribution limit: S$15,300 (non-citizen) / S$35,700 (year 1 extra)
- Expected growth rate: 8% (configurable)
- Tax relief rate: 19.5% of contribution
- Withdrawal: After age 62, over 10 years
- Tax on withdrawal: 50% taxable (with S$550/year on 60K withdrawal)

**Projections:**
- Year-by-year accumulation table
- Impact scenarios (contribute till 50 vs contribute less years)
- Withdrawal schedule projections

### 5.4 Deposit/Withdrawal Tracking

Track cash flows per broker account:
- When money was deposited into each broker
- When money was withdrawn
- Running total per account
- Helps calculate true return on invested capital

---

## 6. Multi-Tenancy & Multi-Owner Design

### 6.1 Tenant isolation (security boundary)

Every user-owned table carries a `user_id`. Reads go through `findByUserId...` finders; writes stamp the authenticated user from `TenantContext`. Cross-tenant access is impossible and is guarded by `MultiTenantIsolationTest`. This is a security boundary, not just a data-modelling choice.

### 6.2 Owners (within a tenant)

Inside one user's data, `Owner` records attribute finances to people (relationships: SELF, SPOUSE, SON, DAUGHTER, FATHER, MOTHER, BROTHER, SISTER). A user typically creates at least themselves, and optionally a spouse or family members. Owners are user-created — there is **no seed data**.

### 6.3 Consolidated vs Individual Views

- **Dashboard**: consolidated by default; filter by owner.
- **Portfolio / most modules**: filter by owner.
- **Reports**: per-owner or combined.

### 6.4 Data Separation

Investment tables include both `user_id` (tenant) and `owner_id` (person):
- Holdings are unique per (asset, account, owner).
- Transactions, dividends and snapshots are tagged with the owner.
- An owner cannot be deleted while records still reference it (409 with references).

---

## 7. Currency Handling Design

> The authoritative, current description of the currency model is in the **§ Currency Handling** section at the end of this document. The summary below is kept for orientation and has been reconciled with the implementation.

### 7.1 Storage Rules

1. Every record stores its **original currency + amount** — the source of truth. Conversion never overwrites it.
2. Amounts are stored in whatever currency the user entered (e.g. USD for a US ETF, LKR for a Sri Lanka fixed deposit); there is no assumed per-module currency.
3. Exchange rates are stored per user (`currency_rates`) with an effective date.
4. Conversion to the user's base or a display currency is done at **read time** by `CurrencyConversionService`.

### 7.2 Display Modes

| Context | Value shown |
|---------|-------------|
| Individual record / list row | Original currency + amount (unchanged) |
| Net Worth / Dashboard totals | User's **base currency** (converted) |
| Module summary endpoints | User's base currency (converted; response includes `baseCurrency`) |
| Display-currency toggle | The chosen display currency (re-derived; never persisted) |

### 7.3 Exchange Rate Management

- User-maintained via the FX Rates screen. **No external feed.**
- Rates stored with an effective date; the latest by date is used, with direct → inverse → identity resolution.
- **No hardcoded default rates** exist anywhere in the codebase; a fresh install has no rates until the user adds them.

---

## 8. Chart Specifications

### 8.1 Chart Library: Recharts

| Chart | Type | Page | Data |
|-------|------|------|------|
| Net Worth Over Time | Stacked Area | Dashboard, Reports | Year-end values by type |
| Asset Allocation | Donut/Pie | Dashboard, Planning | Current $ by asset type |
| Actual vs Target | Horizontal Bar | Planning | % comparison |
| Account Values | Bar Chart | Dashboard | Value per broker |
| Monthly Investment | Bar Chart | Reports | Buy vs Sell volume |
| Dividend Growth | Line Chart | Dividends | Yearly dividend income |
| FD by Bank | Pie Chart | Fixed Deposits | Principal by bank |
| FD Maturity Timeline | Gantt/Timeline | Fixed Deposits | Start to maturity dates |
| SRS Projection | Area Chart | Planning | Accumulated value over years |
| Performance by Broker | Bar Chart | Reports | P&L per broker |
| Top/Bottom Performers | Horizontal Bar | Dashboard | % gain/loss by stock |
| YoY Net Worth Growth | Combo (Bar + Line) | Reports | Value bars + % line |

### 8.2 Chart Color Scheme

```
Index Fund:      #6366f1 (Indigo)
Growth Equity:   #06b6d4 (Cyan)
Mutual Fund:     #10b981 (Emerald)
Dividend Equity: #8b5cf6 (Violet)
Leveraged ETF:   #f97316 (Orange)
Money Market:    #14b8a6 (Teal)
Fixed Deposit:   #64748b (Slate)
Savings:         #eab308 (Yellow)
Crypto:          #f59e0b (Amber)
```

---

## 9. Error Handling & Validation

### 9.1 Backend Validation Rules

| Entity | Field | Rule |
|--------|-------|------|
| Fixed Deposit | principalAmount | Must be > 0 |
| Fixed Deposit | interestRate | 0-100% |
| Fixed Deposit | maturityDate | Must be after startDate |
| Transaction | quantity | Must be > 0 |
| Transaction | pricePerUnit | Must be >= 0 |
| Allocation Target | targetPercentage | 0-100, all must sum to 100 |
| SRS Plan | contribution | Max 15,300/year (or 35,700 first year) |

### 9.2 Frontend Validation

- Required field indicators
- Inline error messages below fields
- Toast notifications for API errors
- Optimistic updates with rollback on failure

---

## 10. Data Import Strategy (Future)

### 10.1 Excel Import

Support importing data from the existing Excel spreadsheets:

1. **Investment Import**: Parse the Funds tabs to bulk-create transactions
2. **FD Import**: Parse the Fixed Deposits sheet to create FD records
3. **History Import**: Parse snapshot tabs to create historical net worth records

### 10.2 Import Format

CSV format with headers matching the API request structure:
```csv
date,asset_symbol,account,type,quantity,price,currency,notes
2024-03-15,VOO,Tiger,BUY,10,470.22,USD,"Monthly purchase"
```

---

## 11. Performance Considerations

### 11.1 Query Optimization

- Indexed fields: `owner_id`, `account_id`, `asset_id`, `transaction_date`, `status`
- Composite indexes: `(asset_id, account_id, owner_id)` on holdings
- Pagination for transaction lists (default 50 per page)
- Lazy loading for charts (load visible data first)

### 11.2 Caching Strategy

- Dashboard summary: Cache for 5 minutes (invalidate on new transaction)
- Exchange rates: Cache until manually updated
- FD summary: Cache until FD is created/updated
- Net worth history: Cache permanently (historical data doesn't change)

---

## 12. Testing Strategy

### 12.1 Backend Testing

| Layer | Tool | Focus | Tests |
|-------|------|-------|-------|
| Integration Tests | SpringBootTest + MockMvc + H2 | Full API flow, auth, multi-tenant | 181 |
| Service Tests | SpringBootTest + JUnit 5 | Business logic, transactions | 15 |
| Security Tests | JUnit 5 + JwtService | Token generation/validation | 10 |

**Coverage: 84.5% instruction / 87.4% line**

### 12.2 Frontend Testing

| Layer | Tool | Focus | Tests |
|-------|------|-------|-------|
| Component Tests | Vitest + React Testing Library | Toast system, AuditTrail page | 25 |
| Module Tests | Vitest | API exports, type definitions | 22 |

**Coverage: 90.2% lines**

### 12.3 Test Suites Summary (228 total)

| Suite | Type | Tests | Focus |
|-------|------|-------|-------|
| AuthControllerTest + Extended | Backend | 21 | Login, register, forgot/reset password, JWT |
| UserManagementControllerTest | Backend | 9 | Admin CRUD, role guard |
| AuditControllerTest | Backend | 7 | Admin access, filters, pagination |
| AccountControllerTest | Backend | 4 | Account CRUD, reference checks |
| AssetControllerTest | Backend | 7 | Asset CRUD, types, search |
| TransactionControllerTest | Backend | 4 | Buy/sell, holdings creation |
| FixedDepositControllerTest | Backend | 16 | Full FD CRUD, banks, holders, net-worth |
| InsuranceControllerTest | Backend | 11 | Policies, bonus entries |
| PlanningControllerTest | Backend | 10 | Allocation targets, deposits |
| RetirementFundControllerTest | Backend | 12 | CPF/SRS/EPF entries |
| MultiTenantIsolationTest | Backend | 5 | Cross-user data isolation |
| TransactionServiceTest | Backend | 6 | Buy/sell logic, avg price, errors |
| AccountServiceTest | Backend | 9 | CRUD + reference constraints |
| JwtServiceTest | Backend | 10 | Token gen/validate/claims |
| ToastContainer + Context | Frontend | 15 | Render, dismiss, auto-remove |
| AuditTrail page | Frontend | 10 | Filters, loading, empty state |
| SearchableSelect | Frontend | 7 | Dropdown behavior |

### 12.4 Running Tests

```bash
# Backend (requires JDK 17)
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw test

# Frontend
cd frontend
npm test

# Generate test-results.json for the Test Results page
bash scripts/run-tests.sh
```

### 12.5 Key Test Scenarios Covered

- Transaction creates/updates holding correctly
- Average buy price calculation with multiple buys
- Sell transaction reduces holding; oversell throws error
- Multi-tenant data isolation (User A cannot see User B's data)
- Admin-only endpoint guard (403 for non-admin)
- JWT token generation, validation, expiry, tampering detection
- Reference constraint enforcement (cannot delete account with holdings)
- Audit log creation, filtering by action/entity/date
- Toast notification rendering, auto-dismiss, manual dismiss
- Password reset flow (forgot → token → reset)

---

## 13. Security & Authentication

### 13.1 Authentication System

| Feature | Implementation |
|---------|---------------|
| Auth mechanism | JWT (HS512) with 24-hour expiry |
| Password storage | BCrypt encoded |
| Self-registration | Disabled (admin creates users) |
| Password reset | Token-based with 1-hour expiry |
| Session | Stateless (no server-side sessions) |
| Inactivity logout | Frontend auto-logout after 1 hour |

### 13.2 Authorization

| Role | Access |
|------|--------|
| USER | All financial data (own data only via multi-tenant) |
| ADMIN | User Management, Audit Trail, Test Results, Docs |

### 13.3 Multi-Tenant Isolation

All data queries filter by `userId` via `TenantContext.getCurrentUserId()`. Each entity table has a `userId` column. The JWT token carries `userId` and `role` claims.

### 13.4 Security Configuration

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/h2-console/**").permitAll()
    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
    .anyRequest().authenticated()
)
```

### 13.5 Audit Trail

All CREATE/UPDATE/DELETE operations are automatically logged via AOP (`AuditAspect`):

| Field | Description |
|-------|-------------|
| userId | Who performed the action |
| username | Username for display |
| action | CREATE, UPDATE, DELETE |
| entity | Controller name (Account, Asset, etc.) |
| entityId | ID of affected record |
| details | Method name for context |
| timestamp | When it happened |

Admin can query audit logs with filters: action, entity, date range, pagination.

---

## 14. Logging & Observability

### 14.1 Backend Logging

| Layer | Level | What's logged |
|-------|-------|---------------|
| Controllers | INFO | All CREATE/UPDATE/DELETE with entity + ID |
| Controllers | DEBUG | GET requests with params |
| Services | INFO | Successful mutations |
| Services | WARN | Business rule violations (reference constraints) |
| Services | ERROR | Unexpected failures |
| Security | DEBUG | JWT validation failures |
| GlobalExceptionHandler | WARN/ERROR | All unhandled exceptions |
| RequestLoggingFilter | INFO | Every HTTP request with method, path, status, duration |

**Log file:** `backend/logs/myfinance.log`

**Log format:** `yyyy-MM-dd HH:mm:ss [thread] LEVEL logger - message`

### 14.2 Frontend Logging

All API calls logged to browser console:
```
[API] POST /bank-savings {"accountName":"DBS","balance":5000,...}
[API] 201 POST /bank-savings
[API] 403 GET /api/audit {error details}
```

### 14.3 Configuration (application.yml)

```yaml
logging:
  level:
    com.myfinance: DEBUG
    org.springframework.web: INFO
  file:
    name: ./logs/myfinance.log
```

---

## 15. UI Notification System

### Toast Notifications

Replaced all `alert()` calls with an inline toast system:

| Type | Color | Use case |
|------|-------|----------|
| error | Red banner | API failures, validation errors |
| success | Green banner | Create/update confirmations |
| info | Blue banner | Informational messages |

**Behavior:**
- Auto-dismiss after 4 seconds
- Manual dismiss via X button
- Stacked (multiple toasts visible)
- Slide-in animation from right
- Positioned fixed top-right (z-9999)


## Currency Handling

MyFinance preserves the **original currency and amount** of every financial record. Base and display currencies are derived views, never a replacement for the stored value.

### Three concepts
1. **Original currency + amount** — what the user entered/received/purchased. The source of truth. Stored on the record; never overwritten by conversion.
2. **Base currency** — per-user (`app_users.base_currency`, null → SGD). Used for consolidated figures (Net Worth, module summary totals).
3. **Display currency** — per-user list (`app_users.display_currencies`, null → `SGD,USD`), selectable via a UI toggle. Purely presentational.

### Conversion (`CurrencyConversionService`)
- Converts any amount to the user's base (or a display currency) using the user's own `currency_rates` rows — latest by `effective_date`. Resolution: direct rate → inverse rate → identity (1.0) with a warning. **No hardcoded FX** on client or server.
- `toBase(amount, currencyCode, userId)`, `factorToBase(...)`, `factorFromBase(...)`, `getBaseCurrency(userId)`, `getDisplayCurrencies(userId)`.

### Where conversion happens
- **Net Worth / Dashboard** (`DashboardService`, `NetWorthService`): every holding and standalone module (Bank Savings, Property equity, Precious Metals, Generic FD) is converted to base before summing. `DashboardSummary` returns `baseCurrency` and `displayRates` (base→display factors from the user's rates) so the frontend converts display values without constants.
- **Module summary endpoints** (`/bank-savings/summary`, `/precious-metals/summary`, `/properties/summary`, `/tax/summary`, ...): convert each record to base before summing; response includes `baseCurrency`. Row/list endpoints return the untouched original currency + amount.

### Broker account vs investment currency
These are distinct and both preserved:
- `Account.currency` — the broker/settlement currency.
- `Asset.currency` / `Holding.currency` — the instrument's currency. A `Holding` carries the **asset's** currency, not the broker account's.
- `Transaction.currency` — the settlement currency (defaults to the account's).
- Example: buying an **EUR** fund through a **USD** broker → `Holding.currency = EUR`, `Transaction.currency = USD`. Neither is overwritten by the other.

### Per-entity currency source
- **Currency enum:** Account, BankSavings, Dividend, Holding, Asset, Transaction, SoldPosition, AccountDeposit, HomeLoan, InsurancePolicy.
- **String currency:** Property, PreciousMetal, GenericFixedDeposit, and (added) SalaryRecord, TaxRecord, RetirementFundEntry, FixedDeposit (SL). All nullable → treated as the user's base when null.
- **Base-only by design (no currency field):** CPF/SRS and Budget modules.

### Migration / backward compatibility
All currency columns are **additive and nullable**; existing rows read as the user's base currency. No historical value was rewritten. Where a record historically stored only a base-currency figure (e.g. SL FixedDeposit `net_worth_amount`), the original currency is not fabricated — it defaults to base and `net_worth_amount` is treated as a derived/cached override of `principal_amount` (+ `currency`).

## 16. Table Export (CSV / Excel / PDF)

Data tables that contain row-level financial records let the user download the table in three formats: **CSV**, **Excel (.xlsx)** and **PDF**.

### 16.1 Scope — which tables

Export is added only to tables where downloading a dataset makes business sense (row-level records the user owns):

- **Transactions** (`/transactions`)
- **Accounts** (`/accounts`, Accounts tab)
- **Dividends** (`/dividends`)
- **Portfolio → Holdings** (`/portfolio`, Holdings tab)

Aggregate/summary and configuration screens (Dashboard, Reports charts, Net Worth Config, etc.) deliberately do not get an export button.

### 16.2 Architecture — client-side, reusable

Exports are generated **on the frontend** from the data already loaded on the page. This is appropriate here because the list APIs return the **complete per-user dataset** (filtered by `userId`; the app has **no server-side pagination**), so the browser already holds the full authorized dataset — there is no separate export endpoint to build and no risk of exporting only "the current page".

The mechanism is reusable and config-driven:

```
Table page
   └── <ExportMenu rows={data} config={ExportConfig} />
          └── exportTable(rows, config, format)
                 ├── CSV   (utils/export/csv.ts)
                 ├── Excel (utils/export/excel.ts, ExcelJS)
                 └── PDF   (utils/export/pdf.ts, jsPDF + autotable)
```

- `ExportConfig` (`frontend/src/utils/export/configs.ts`) declares `entity`, `title`, and an ordered `columns[]` list. Each column has a `key`, `header`, `accessor(row)` and a logical `type` (`string | number | currency | date | datetime | boolean`).
- Adding export to a new table = add a config + drop `<ExportMenu>` on the page. No new backend code.
- Libraries: `exceljs` (Excel), `jspdf` + `jspdf-autotable` (PDF), hand-rolled RFC-4180 writer (CSV). `xlsx`/SheetJS was intentionally **not** used because its npm build carries unfixable high-severity advisories.

### 16.3 Export scope, filters and sorting

- **All columns:** the export contains **every user-relevant column** of the underlying data model, including columns the on-screen table hides (record IDs, fees, original currency, notes, created/updated timestamps). It is **not** limited to the rendered UI columns.
- **Complete dataset:** the export contains the full dataset the page holds, **not** just the visible page.
- **Filters & sorting:** the export reflects the table's **current filters and sort order** — it serialises exactly the array the page currently holds (e.g. Dividends respects the owner/broker/year filters; Transactions respects the owner filter, noted as a subtitle in the PDF).

### 16.4 Currency and financial values

Following the app's currency model, the **original amount and original currency code are preserved verbatim**. Values are **never** converted to the base currency during export. Monetary columns are exported as raw numbers (Excel keeps them numeric); the currency code is a separate column.

### 16.5 Date/time

Dates are emitted in ISO form (`YYYY-MM-DD`), datetimes as `YYYY-MM-DD HH:mm:ss`. Excel keeps dates as native date cells. The underlying value is never mutated for presentation.

### 16.6 Format specifics

- **CSV** — UTF-8 with BOM (so Excel detects encoding), CRLF line endings, RFC-4180 quoting/escaping (commas, quotes, newlines), and a formula-injection guard (leading `= + - @` prefixed with `'`). Nulls become empty fields.
- **Excel** — one worksheet named after the table title, bold header row, **frozen header**, auto-sized columns (bounded), numeric/currency cells stay numeric, dates stay dates.
- **PDF** — report title, generated timestamp, optional subtitle (active filters), repeated header on every page, page numbers, and **landscape orientation for wide tables** (> 6 columns) with line-wrapped long text.

### 16.7 Authorization, tenant isolation & security

- Authorization and tenant isolation are enforced **server-side** by the same list endpoints that feed the tables (`findByUserId…` + `TenantContext.getCurrentUserId()`). A user can only ever export rows the API already returned for them; there is no way to request another tenant's data.
- `ExportDataSourceSecurityTest` pins this guarantee: the export data-source endpoints (transactions, accounts, dividends, holdings) require authentication and return only the caller's records.
- **Sensitive fields are excluded** from configs (e.g. broker account numbers, which are masked in the UI, are not in the Accounts export). "All columns" means all user-relevant business columns, never secrets/internal metadata.
- Exports are generated in the browser from already-authorized data — nothing is written to disk on the server, no dataset is put in a URL, and no full dataset is logged.

### 16.8 File naming

`<entity>_<YYYY-MM-DD>.<ext>` — e.g. `transactions_2026-08-31.csv`, `accounts_2026-08-31.xlsx`, `holdings_2026-08-31.pdf`.

### 16.9 UX & accessibility

- A single **Export ▼** dropdown offers CSV / Excel / PDF. It shows an **Exporting…** state, a success toast ("Your export is ready.") and a generic error toast ("Unable to export the data. Please try again.") — never a stack trace.
- Duplicate concurrent exports are prevented (the control disables while an export is in flight); the control is disabled when the dataset is empty.
- The menu uses proper `role="menu"`/`menuitem` semantics with `aria-haspopup`/`aria-expanded`, full keyboard navigation (Arrow keys, Home/End, Enter/Space, Esc) and focus management.

### 16.10 Large datasets

Because the app has no server-side pagination and datasets are per-user (not multi-tenant aggregates), client-side generation is sufficient and avoids extra server round-trips. If a future module introduces server-side pagination or genuinely large datasets, a backend streaming export endpoint can be added behind the same `ExportConfig` contract without changing callers.
