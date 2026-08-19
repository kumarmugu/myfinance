# MyFinance - Design Document

## 1. Database Schema

### 1.1 Core Tables

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
| beneficiary | VARCHAR(30) | | Who benefits (appa/amma indicator) |
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
    "holder": { "id": 1, "name": "K.Kamaleswary", "isSeniorCitizen": true },
    "jointHolder": { "id": 2, "name": "Kumarasamy" },
    "bank": { "id": 1, "name": "National Savings Bank", "shortName": "NSB" },
    "accountNumber": "2/0073/04/18170",
    "principalAmount": 575000.00,
    "interestRate": 8.0,
    "startDate": "2025-01-11",
    "maturityDate": "2026-01-11",
    "period": "12 Months",
    "branch": "Chavakachcheri",
    "category": "SENIOR_CITIZEN",
    "status": "ACTIVE",
    "expectedInterest": 46000.00,
    "beneficiary": "APPA",
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
  "totalPrincipal": 32923114.22,
  "totalExpectedInterest": 2434725.09,
  "totalActiveFDs": 41,
  "byBank": [
    { "bank": "BOC", "count": 20, "amount": 19103684.77, "percentage": 58 },
    { "bank": "NSB", "count": 7, "amount": 6727062.53, "percentage": 20 },
    { "bank": "Seylan", "count": 9, "amount": 4670506.05, "percentage": 14 }
  ],
  "byBeneficiary": [
    { "beneficiary": "Appa", "count": 23, "amount": 20305406.84, "percentage": 61 },
    { "beneficiary": "Amma", "count": 18, "amount": 12617707.38, "percentage": 39 }
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
    "holder": "Kiri/Amma",
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
  "totalInterestIncome": 2434725.09,
  "byHolder": [
    { "holder": "Appa", "interest": 1592251.12 },
    { "holder": "Amma", "interest": 842473.97 }
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
    "totalPrincipal": 32923114.22,
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
    "invested": 137419.41,
    "currentValue": 196423.12,
    "profit": 59003.72,
    "profitPercentage": 67.63,
    "currency": "USD"
  },
  {
    "broker": "Saxo",
    "invested": 107434.67,
    "currentValue": 154550.82,
    "profit": 34550.82,
    "profitPercentage": 32.16,
    "currency": "SGD"
  },
  {
    "broker": "IBKR",
    "invested": 153973.48,
    "currentValue": 178634.78,
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
│ │ S$910,269  │ │ S$750,000  │ │ +S$160,269 │ │ S$2,051   │ │
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
│ │ 1 │K.Kamaleswary│NSB  │575,000   │8%  │11/01/25│11/01/26│ │
│ │ 2 │K.Kamaleswary│Comm │194,000   │8%  │28/01/25│28/01/26│ │
│ │ 3 │Kumarasamy   │HNB  │1,030,000 │8%  │22/05/25│22/05/26│ │
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
│ │ 2021 │ S$392,548  │ -         │ -         │ Starting     │ │
│ │ 2022 │ S$658,104  │ +S$265,556│ +67.65%   │              │ │
│ │ 2023 │ S$910,269  │ +S$252,165│ +38.32%   │              │ │
│ │ 2024 │ S$902,104  │ -S$8,166  │ -0.90%    │ Market adj   │ │
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
| **Appa & Amma** | K.Kamaleswary, Kumarasamy | Parents' FDs |
| **Mugu** | Mugu/Appa, Mugu/Amma | User's FDs (joint with parents) |
| **Kiri** | Kiri/Appa, Kiri/Amma, Kirivarnan | Sibling's FDs |
| **Anna** | Anna/Appa, Anna/Amma | Another sibling's FDs |
| **Car** | Mugu/Amma, Mugu/Appa, Anna/Amma | Purpose-specific FDs |

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
4. **Beneficiary Report** - Split between Appa and Amma accounts
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

## 6. Multi-Owner Design

### 6.1 Owner Profiles

| Owner | Label | Accounts |
|-------|-------|----------|
| Primary | Self | Tiger, Saxo, IBKR, Poems, Moomoo, DBS, OCBC, CIMB, Coinhako, Crypto.com |
| Spouse | Wife | Saxo (separate sub-account), Tiger (separate) |

### 6.2 Consolidated vs Individual Views

- **Dashboard**: Shows consolidated by default, toggle to individual
- **Portfolio**: Filter by owner
- **Reports**: Generate per-owner or combined
- **Fixed Deposits**: Separate entity (family FDs), not tied to personal owners

### 6.3 Data Separation

All investment tables include `owner_id` field for filtering:
- Holdings are unique per (asset, account, owner)
- Transactions tagged with owner
- Snapshots calculated per owner and consolidated
- Dividends recorded per owner

---

## 7. Currency Handling Design

### 7.1 Storage Rules

1. Investment amounts stored in their **native currency** (USD for US stocks, SGD for SG stocks)
2. Fixed deposits stored in **LKR**
3. Exchange rates stored separately
4. Conversion done at **display time** for reporting

### 7.2 Display Modes

| Context | Display |
|---------|---------|
| Individual holding | Native currency |
| Portfolio total | SGD (converted) |
| Dashboard net worth | SGD |
| Fixed deposit | LKR |
| FD in net worth | SGD (converted) |

### 7.3 Exchange Rate Management

- Manual update via settings page
- Rates stored with effective date for historical accuracy
- Default rates: USD/SGD = 1.27, EUR/SGD = 1.49, LKR/SGD = 0.0042

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

| Layer | Tool | Focus |
|-------|------|-------|
| Unit Tests | JUnit 5 + Mockito | Service logic, calculations |
| Integration Tests | Spring Boot Test + H2 | Repository queries, API flow |
| API Tests | MockMvc | Controller endpoints, validation |

### 12.2 Key Test Scenarios

- Transaction creates/updates holding correctly
- Average buy price calculation with multiple buys
- Sell transaction reduces holding properly
- Net worth snapshot calculation accuracy
- FD interest calculation
- Currency conversion accuracy
- Allocation gap calculation

---

## 13. Security Considerations

### 13.1 Current (Single User, Local)

- No authentication required
- CORS configured for localhost only
- H2 console accessible in dev mode only

### 13.2 Future (If Deployed)

- Spring Security with JWT
- HTTPS enforcement
- Rate limiting on APIs
- Input sanitization (already via Bean Validation)
- Sensitive data encryption at rest
