# MyFinance - Architecture Document

## 1. Overview

MyFinance is a personal finance management application designed to track, analyze, and plan investments across multiple brokerage accounts, fixed deposits, cryptocurrencies, and retirement plans. The application supports multi-currency portfolios (SGD, USD, EUR, LKR), multi-owner profiles, and comprehensive reporting with historical tracking.

---

## 2. Tech Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| **Security** | Spring Security + JWT | 6.x | Authentication & authorization |
| **Testing** | JUnit 5 + Vitest | 5.x / 4.x | Backend + Frontend tests |
| **Coverage** | JaCoCo + v8 | | 84.5% backend / 90.2% frontend |
| **Backend** | Java | 17 | Core language |
| **Framework** | Spring Boot | 3.2.5 | REST API, DI, JPA |
| **ORM** | Hibernate / Spring Data JPA | 6.x | Database access |
| **Database** | H2 (dev) / PostgreSQL (prod) | 2.x / 16 | Persistent storage |
| **Build** | Maven | 3.9+ | Dependency management |
| **Frontend** | React | 18.3 | UI framework |
| **Language** | TypeScript | 5.6 | Type-safe frontend |
| **Build Tool** | Vite | 5.4 | Fast dev/build |
| **Styling** | Tailwind CSS | 4.3 | Utility-first CSS |
| **Charts** | Recharts | 3.10 | Data visualization |
| **HTTP** | Axios | 1.19 | API communication |
| **Routing** | React Router | 7.18 | SPA navigation |
| **Icons** | Lucide React | 1.28 | Icon library |

### Future Considerations
- **PostgreSQL** for production (migration from H2)
- **Redis** for caching exchange rates
- **Docker** for containerized deployment

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (SPA)                         │
│   React 18 + TypeScript + Tailwind CSS + Recharts            │
│   ┌──────────┬──────────┬──────────┬──────────┬───────────┐ │
│   │Dashboard │Portfolio │Fixed Dep │Planning  │Admin      │ │
│   │          │& Trading │          │& SRS     │(Audit/Users)│
│   └──────────┴──────────┴──────────┴──────────┴───────────┘ │
│   ┌──────────────────────────────────────────────────────┐   │
│   │  Toast Notifications │ Auth Context │ Inactivity Timer│  │
│   └──────────────────────────────────────────────────────┘   │
│                         Axios HTTP Client (with logging)      │
└────────────────────────────┬────────────────────────────────┘
                             │ REST API (JSON) + JWT Bearer Token
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                      │
│   ┌─────────────────────────────────────────────────────┐   │
│   │           Security Layer (JWT Auth Filter)           │   │
│   ├─────────────────────────────────────────────────────┤   │
│   │           Request Logging Filter                     │   │
│   ├─────────────────────────────────────────────────────┤   │
│   │           Controllers (REST) + @Slf4j               │   │
│   ├─────────────────────────────────────────────────────┤   │
│   │           AOP Audit Aspect (auto-logs mutations)     │   │
│   ├─────────────────────────────────────────────────────┤   │
│   │           Services (Business Logic) + @Slf4j        │   │
│   ├─────────────────────────────────────────────────────┤   │
│   │           Repositories (Spring Data JPA)            │   │
│   ├─────────────────────────────────────────────────────┤   │
│   │           JPA Entities (Domain Model)               │   │
│   └─────────────────────────────────────────────────────┘   │
│   Log file: ./logs/myfinance.log                             │
└────────────────────────────┬────────────────────────────────┘
                             │ JDBC
                             ▼
┌─────────────────────────────────────────────────────────────┐
│              Database (H2 dev / PostgreSQL prod)              │
│   Tables: app_users, audit_logs, owners, accounts, assets,   │
│   transactions, holdings, fixed_deposits, bank_savings, ...  │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Module Architecture

The application is organized into the following functional modules:

### 4.1 Core Module
Base entities and services shared across all modules.

- **Owner/Profile Management** - Support multiple users (primary user and spouse)
- **Account Management** - Brokers (Tiger, Saxo, IBKR, Poems, Moomoo) and Banks (DBS, OCBC, CIMB)
- **Currency Management** - Multi-currency support with user-maintained exchange rates. Every record preserves its **original currency + amount** (source of truth); a per-user **base currency** (default SGD) drives Net Worth consolidation and a per-user **display-currency** toggle re-derives shown values via FX — conversions never overwrite stored originals. No hardcoded FX. See docs/DESIGN.md § Currency Handling.

### 4.2 Investment Portfolio Module
Manages stock/ETF/fund investments across multiple brokers.

- **Asset Management** - Stocks, ETFs, Mutual Funds with sub-type categorization
- **Transaction Management** - Buy/Sell with full history
- **Holdings Tracking** - Current positions with P&L calculation
- **Sold Positions** - Historical sold position tracking with realized gains
- **Short-term Trading** - Separate tracking for short-duration trades
- **Dividend Tracking** - Record and report dividend income by instrument/quarter
- **Money Market** - Track money market fund movements

### 4.3 Fixed Deposits Module
Manages family fixed deposits across Sri Lankan banks.

- **FD Management** - Create, track, and manage fixed deposits
- **Maturity Tracking** - Alerts and calendar for upcoming maturities
- **Interest Calculation** - Compute expected interest income
- **Family Member Mapping** - Track which FD belongs to which family member
- **Bank/Branch Management** - Organize by bank and branch
- **Renewal Tracking** - Flag FDs that need renewal

### 4.4 Financial Planning Module
Long-term financial planning and target tracking.

- **Net Worth Tracking** - Year-over-year net worth by account
- **Target Allocation** - Define target % and track actual vs target
- **SRS Planning** - Supplementary Retirement Scheme projections
- **Retirement Planning** - Long-term compound growth projections
- **Deposit/Withdrawal Tracking** - Cash flow per broker account

### 4.5 Cryptocurrency Module
Track crypto holdings across exchanges.

- **Crypto Portfolio** - Holdings across Coinhako and Crypto.com
- **Multi-coin Support** - BTC, ETH, SOL, XRP, and altcoins
- **Profit/Loss Tracking** - Track cost basis and current values

### 4.6 Reporting & Analytics Module
Comprehensive dashboards and reports.

- **Dashboard** - Overall financial health summary
- **Net Worth History** - Historical net worth with stacked area charts
- **Performance Reports** - By asset type, by broker, by time period
- **Year-over-Year Comparison** - Annual growth tracking
- **Allocation Analysis** - Actual vs target allocation visualization

### 4.7 Security & Administration Module
Authentication, authorization, and audit capabilities.

- **JWT Authentication** - Stateless token-based auth (HS512, 24h expiry)
- **User Management** - Admin creates/manages users, roles (USER/ADMIN)
- **Multi-Tenant Isolation** - TenantContext ensures data isolation per user
- **Audit Trail** - AOP-based auto-logging of all CREATE/UPDATE/DELETE
- **Password Management** - Change, forgot, reset with token
- **Inactivity Logout** - Frontend auto-logout after 1 hour of no activity

### 4.8 Observability Module
Production-grade logging and monitoring.

- **Request Logging** - All HTTP requests logged with method, path, status, duration
- **Application Logging** - @Slf4j on all controllers and services
- **Frontend Logging** - API interceptor logs all requests/responses to console
- **Error Tracking** - GlobalExceptionHandler logs all unhandled exceptions
- **Log File** - `backend/logs/myfinance.log`

---

## 5. Data Model Overview

### 5.1 Core Entities

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│    Owner     │     │   Account    │     │    Asset     │
├──────────────┤     ├──────────────┤     ├──────────────┤
│ id           │     │ id           │     │ id           │
│ name         │     │ name         │     │ name         │
│ relationship │     │ accountType  │     │ symbol       │
│ isActive     │     │ owner_id     │     │ assetType    │
│              │     │ currency     │     │ assetSubType │
│              │     │ description  │     │ currentPrice │
│              │     │              │     │ currency     │
│              │     │              │     │ exchange     │
└──────────────┘     └──────────────┘     └──────────────┘

┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│ Transaction  │     │   Holding    │     │ NetWorthSnapshot  │
├──────────────┤     ├──────────────┤     ├──────────────────┤
│ id           │     │ id           │     │ id                │
│ asset_id     │     │ asset_id     │     │ owner_id          │
│ account_id   │     │ account_id   │     │ snapshotDate      │
│ type         │     │ quantity     │     │ accountBreakdown  │
│ quantity     │     │ avgBuyPrice  │     │ typeBreakdown     │
│ pricePerUnit │     │ investedAmt  │     │ totalNetWorth     │
│ totalAmount  │     │ owner_id     │     │                   │
│ currency     │     │              │     │                   │
│ date         │     │              │     │                   │
└──────────────┘     └──────────────┘     └──────────────────┘
```

### 5.2 Fixed Deposit Entities

```
┌──────────────────┐     ┌──────────────┐     ┌──────────────┐
│  FixedDeposit    │     │  FDHolder    │     │    Bank      │
├──────────────────┤     ├──────────────┤     ├──────────────┤
│ id               │     │ id           │     │ id           │
│ holder_id        │     │ name         │     │ name         │
│ bank_id          │     │ relationship │     │ shortName    │
│ accountNumber    │     │ isSenior     │     │ country      │
│ principalAmount  │     │              │     │              │
│ interestRate     │     │              │     │              │
│ startDate        │     │              │     │              │
│ maturityDate     │     │              │     │              │
│ period           │     │              │     │              │
│ branch           │     │              │     │              │
│ category         │     │              │     │              │
│ status           │     │              │     │              │
│ beneficiary      │     │              │     │              │
│ notes            │     │              │     │              │
└──────────────────┘     └──────────────┘     └──────────────┘
```

### 5.3 Planning Entities

```
┌──────────────────────┐     ┌────────────────────┐
│  AllocationTarget    │     │  SRSPlan           │
├──────────────────────┤     ├────────────────────┤
│ id                   │     │ id                 │
│ owner_id             │     │ owner_id           │
│ assetType            │     │ year               │
│ targetPercentage     │     │ contribution       │
│ targetAmount         │     │ accumulatedValue   │
│                      │     │ expectedGrowthRate │
│                      │     │                    │
└──────────────────────┘     └────────────────────┘

┌──────────────────────┐     ┌────────────────────┐
│  AccountDeposit      │     │  Dividend          │
├──────────────────────┤     ├────────────────────┤
│ id                   │     │ id                 │
│ account_id           │     │ asset_id           │
│ amount               │     │ account_id         │
│ depositDate          │     │ amount             │
│ type (DEPOSIT/       │     │ currency           │
│       WITHDRAWAL)    │     │ receivedDate       │
│                      │     │ quarter            │
└──────────────────────┘     └────────────────────┘
```

---

## 6. Enum Definitions

### AccountType
```
BROKER          - Stock/Fund brokerage (Tiger, Saxo, IBKR, Poems, Moomoo)
BANK            - Bank savings account (DBS, OCBC, CIMB)
CRYPTO_EXCHANGE - Cryptocurrency exchange (Coinhako, Crypto.com)
FIXED_DEPOSIT   - Fixed deposit account
```

### AssetType
```
INDEX_FUND       - Index tracking ETFs (VOO, VGT, QQQ, SPYL, QQQM)
MUTUAL_FUND      - Actively managed funds (Fidelity, Amundi, ARK)
GROWTH_EQUITY    - Growth stocks (TSLA, AAPL, NVDA, META, GOOGL)
DIVIDEND_EQUITY  - Dividend stocks (DBS, OCBC, Singtel, REITs)
LEVERAGED_ETF    - 3x leveraged ETFs (TQQQ, SPXL)
MONEY_MARKET     - Money market funds
FIXED_DEPOSIT    - Fixed deposits
SAVINGS          - Bank savings/cash
CRYPTO           - Cryptocurrencies
```

### TransactionType
```
BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL, TRANSFER, INTEREST
```

### Currency
```
SGD, USD, EUR, LKR
```

### FDStatus
```
ACTIVE, MATURED, RENEWED, CLOSED, REQUIRES_UPDATE
```

### OwnerRelationship
```
SELF, SPOUSE, PARENT_FATHER, PARENT_MOTHER, SIBLING
```

---

## 7. API Design

### 7.1 Core APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/owners` | List all owners/profiles |
| POST | `/api/owners` | Create owner |
| GET | `/api/accounts` | List accounts (filter by owner, type) |
| POST | `/api/accounts` | Create account |
| GET | `/api/currencies/rates` | Get current exchange rates |

### 7.2 Investment APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/assets` | List assets (filter by type, exchange) |
| POST | `/api/assets` | Create asset |
| GET | `/api/transactions` | List transactions (filter by date, asset, account) |
| POST | `/api/transactions` | Record transaction |
| GET | `/api/holdings` | Active holdings (filter by owner, account, type) |
| GET | `/api/holdings/sold` | Sold position history |
| GET | `/api/holdings/short-term` | Short-term trades |
| GET | `/api/dividends` | Dividend history |
| POST | `/api/dividends` | Record dividend |

### 7.3 Fixed Deposit APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/fixed-deposits` | List all FDs (filter by holder, bank, status) |
| POST | `/api/fixed-deposits` | Create FD |
| PUT | `/api/fixed-deposits/{id}` | Update FD |
| GET | `/api/fixed-deposits/maturing` | FDs maturing within N days |
| GET | `/api/fixed-deposits/summary` | Summary by bank/holder |
| GET | `/api/fixed-deposits/interest-report` | Expected interest income |
| GET | `/api/fd-holders` | List FD holders (family members) |
| GET | `/api/banks` | List banks |

### 7.4 Planning APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/planning/allocation` | Current vs target allocation |
| PUT | `/api/planning/allocation/targets` | Set allocation targets |
| GET | `/api/planning/srs` | SRS plan projections |
| PUT | `/api/planning/srs` | Update SRS plan |
| GET | `/api/planning/retirement` | Retirement projections |
| GET | `/api/planning/net-worth/history` | Historical net worth |
| POST | `/api/planning/net-worth/snapshot` | Take snapshot |

### 7.5 Reporting APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/reports/dashboard` | Dashboard summary |
| GET | `/api/reports/performance` | Performance by type/broker |
| GET | `/api/reports/yoy` | Year-over-year comparison |
| GET | `/api/reports/deposits-withdrawals` | Cash flow by account |
| GET | `/api/reports/dividends/summary` | Dividend summary by year/quarter |

---

## 8. Frontend Architecture

### 8.1 Page Structure

```
/                           → Dashboard (overall financial health)
/portfolio                  → Investment portfolio (holdings, P&L)
/portfolio/sold             → Sold positions history
/portfolio/short-term       → Short-term trading log
/transactions               → Transaction management
/dividends                  → Dividend tracking
/fixed-deposits             → Fixed deposit management
/fixed-deposits/reports     → FD reports and analytics
/planning                   → Financial planning hub
/planning/allocation        → Target vs actual allocation
/planning/srs               → SRS retirement planning
/planning/projections       → Growth projections
/reports                    → Comprehensive reports
/reports/net-worth          → Net worth history
/reports/performance        → Performance analysis
/reports/yoy               → Year-over-year comparison
/accounts                   → Account/broker management
/assets                     → Asset/stock management
/settings                   → App settings, currency rates
/docs                       → Architecture & design docs
```

### 8.2 Component Architecture

```
App
├── Layout (Sidebar + Main Content)
│   ├── Sidebar Navigation
│   └── Content Area
│       ├── Dashboard
│       │   ├── NetWorthCard
│       │   ├── AllocationPieChart
│       │   ├── NetWorthHistoryChart
│       │   ├── TopPerformersTable
│       │   └── RecentActivityFeed
│       ├── Portfolio
│       │   ├── HoldingsTable
│       │   ├── PerformanceByTypeChart
│       │   ├── BrokerBreakdownChart
│       │   └── SoldPositionsTable
│       ├── FixedDeposits
│       │   ├── FDListTable
│       │   ├── MaturityCalendar
│       │   ├── InterestSummaryCard
│       │   ├── BankBreakdownChart
│       │   └── FDForm (Add/Edit)
│       ├── Planning
│       │   ├── AllocationTargetChart
│       │   ├── SRSProjectionTable
│       │   ├── RetirementChart
│       │   └── DepositHistoryTable
│       └── Reports
│           ├── NetWorthAreaChart
│           ├── YoYComparisonChart
│           ├── DividendSummaryTable
│           └── CashFlowChart
```

---

## 9. Multi-Currency Strategy

The application handles multiple currencies with the following approach:

1. **Storage**: All monetary values stored with their original currency
2. **Display**: User can toggle between original currency and SGD equivalent
3. **Exchange Rates**: Stored rates table with manual/automatic updates
4. **Conversion**: Applied at read-time for reporting, not stored as converted values

### Currency Fields
- `amount` - The monetary value
- `currency` - ISO 4217 code (SGD, USD, EUR, LKR)

### Exchange Rate Table
| From | To | Rate | Updated |
|------|----|------|---------|
| USD | SGD | 1.27 | Manual update |
| EUR | SGD | 1.49 | Manual update |
| LKR | SGD | 0.0042 | Manual update |

---

## 10. Multi-Owner Strategy

The application supports tracking finances for multiple family members:

| Owner | Role | Portfolios |
|-------|------|------------|
| Primary User | Self | Tiger, Saxo, IBKR, Poems, Moomoo, Coinhako, Crypto.com, DBS, OCBC, CIMB |
| Spouse | Spouse | Saxo (separate), Tiger (separate) |
| Parents | Family | Fixed Deposits (Sri Lanka) |

### Data Isolation
- Each holding, transaction, and snapshot is tagged with `owner_id`
- Reports can filter by owner or show consolidated view
- Fixed deposits have separate `holder` entities for family members

---

## 11. Deployment Architecture

### Development
```
Frontend: localhost:5173 (Vite dev server)
Backend:  localhost:8080 (Spring Boot embedded Tomcat)
Database: H2 file-based (./data/myfinance.mv.db)
```

### Production (Future)
```
Frontend: Static files served via Nginx or Spring Boot static resources
Backend:  Spring Boot JAR on Docker/VPS
Database: PostgreSQL
```

---

## 12. Directory Structure

```
myfinance/
├── backend/
│   ├── src/main/java/com/myfinance/
│   │   ├── MyFinanceApplication.java
│   │   ├── config/
│   │   │   ├── AdminUserInitializer.java
│   │   │   ├── AuditAspect.java          ← AOP audit interceptor
│   │   │   ├── DataInitializer.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   ├── JacksonConfig.java
│   │   │   ├── ReferenceConstraintException.java
│   │   │   └── RequestLoggingConfig.java  ← HTTP request logger
│   │   ├── controller/
│   │   │   ├── AccountController.java
│   │   │   ├── AssetController.java
│   │   │   ├── AuditController.java       ← Admin audit trail
│   │   │   ├── AuthController.java        ← Login/register/reset
│   │   │   ├── BankSavingsController.java
│   │   │   ├── CurrencyRateController.java
│   │   │   ├── DashboardController.java
│   │   │   ├── DividendController.java
│   │   │   ├── FixedDepositController.java
│   │   │   ├── HoldingController.java
│   │   │   ├── HomeLoanController.java
│   │   │   ├── InsuranceController.java
│   │   │   ├── NetWorthConfigController.java
│   │   │   ├── OwnerController.java
│   │   │   ├── PlanningController.java
│   │   │   ├── RetirementFundController.java
│   │   │   ├── SalaryController.java
│   │   │   ├── SoldPositionController.java
│   │   │   ├── TaxController.java
│   │   │   ├── TransactionController.java
│   │   │   ├── UserManagementController.java ← Admin user CRUD
│   │   │   └── WorkExperienceController.java
│   │   ├── dto/
│   │   │   ├── AuthRequest.java
│   │   │   ├── AuthResponse.java
│   │   │   ├── ChangePasswordRequest.java
│   │   │   ├── DashboardSummary.java
│   │   │   ├── ForgotPasswordRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   ├── ResetPasswordRequest.java
│   │   │   └── TransactionRequest.java
│   │   ├── model/
│   │   │   ├── Account.java
│   │   │   ├── AccountDeposit.java
│   │   │   ├── AllocationTarget.java
│   │   │   ├── AppUser.java              ← User entity
│   │   │   ├── Asset.java
│   │   │   ├── AuditLog.java             ← Audit trail entity
│   │   │   ├── Bank.java
│   │   │   ├── BankSavings.java
│   │   │   ├── CurrencyRate.java
│   │   │   ├── Dividend.java
│   │   │   ├── FDHolder.java
│   │   │   ├── FixedDeposit.java
│   │   │   ├── Holding.java
│   │   │   ├── InsurancePolicy.java
│   │   │   ├── NetWorthSnapshot.java
│   │   │   ├── Owner.java
│   │   │   ├── RetirementFundEntry.java
│   │   │   ├── SoldPosition.java
│   │   │   ├── Transaction.java
│   │   │   └── enums/
│   │   ├── repository/
│   │   │   └── (one per entity, 25 total)
│   │   ├── security/
│   │   │   ├── CustomUserDetailsService.java
│   │   │   ├── JwtAuthFilter.java
│   │   │   ├── JwtService.java
│   │   │   ├── SecurityConfig.java
│   │   │   └── TenantContext.java
│   │   └── service/
│   │       ├── AccountService.java
│   │       ├── AssetService.java
│   │       ├── AuditService.java
│   │       ├── DashboardService.java
│   │       ├── HoldingService.java
│   │       ├── NetWorthService.java
│   │       ├── OwnerService.java
│   │       └── TransactionService.java
│   ├── src/test/java/com/myfinance/
│   │   ├── controller/          (23 test files, 165 tests)
│   │   ├── service/             (2 test files, 15 tests)
│   │   └── security/            (1 test file, 10 tests)
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── logs/
│   │   └── myfinance.log       ← Runtime log output
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── index.ts        ← API client with logging interceptor
│   │   ├── components/
│   │   │   ├── SearchableSelect.tsx
│   │   │   └── ToastContainer.tsx   ← Toast notification UI
│   │   ├── contexts/
│   │   │   ├── AuthContext.tsx      ← Auth + inactivity logout
│   │   │   └── ToastContext.tsx     ← Toast state management
│   │   ├── pages/
│   │   │   ├── AuditTrail.tsx       ← Admin audit log viewer
│   │   │   ├── Dashboard.tsx
│   │   │   ├── Portfolio.tsx
│   │   │   ├── BankSavings.tsx
│   │   │   ├── UserManagement.tsx
│   │   │   └── ... (20+ pages)
│   │   ├── test/
│   │   │   ├── setup.ts
│   │   │   ├── api.test.ts
│   │   │   └── types.test.ts
│   │   ├── types/
│   │   │   └── index.ts
│   │   ├── utils/
│   │   │   └── formatters.ts
│   │   ├── App.tsx
│   │   └── index.css            ← Includes toast animation
│   ├── public/
│   │   └── test-results.json    ← Test data for admin page
│   ├── package.json
│   └── vitest.config.ts
├── scripts/
│   ├── run-tests.sh             ← Full test runner
│   └── generate-results.py      ← Test results JSON generator
└── docs/
    ├── ARCHITECTURE.md
    └── DESIGN.md
```

---

## 13. Migration Strategy (from Excel to Application)

### Phase 1: Core Enhancement
1. Add multi-owner support
2. Add multi-currency support
3. Expand `AssetType` enum with sub-types
4. Add `SoldPosition` entity for historical trades

### Phase 2: Fixed Deposits Module
1. Create FD entities (FixedDeposit, FDHolder, Bank)
2. Build FD management CRUD
3. Implement maturity tracking and reporting
4. Build interest calculation service

### Phase 3: Financial Planning
1. Implement target allocation tracking
2. Build SRS planning projections
3. Add deposit/withdrawal tracking per account
4. Create year-over-year comparison reports

### Phase 4: Advanced Reporting
1. Historical net worth charts by year
2. Dividend income tracking and reporting
3. Performance attribution (by broker, by asset type)
4. Export capabilities

---

## 14. Non-Functional Requirements

| Requirement | Target | Status |
|-------------|--------|--------|
| Response Time | < 500ms for all API calls | Met (logged via RequestLoggingFilter) |
| Data Integrity | ACID transactions for financial operations | Met |
| Security | JWT auth, multi-tenant isolation, BCrypt passwords | Implemented |
| Test Coverage | 80%+ code coverage | Met (84.5% backend, 90.2% frontend) |
| Logging | Production-grade structured logging | Implemented (file + console) |
| Audit | All mutations tracked with user/timestamp | Implemented (AOP-based) |
| Session | Auto-logout after 1 hour inactivity | Implemented |
| Browser Support | Chrome, Firefox, Safari (latest) | Met |
| Mobile | Responsive design (Tailwind breakpoints) | Met |

---

## 15. Versioning & Roadmap

| Version | Scope | Status |
|---------|-------|--------|
| **v1.0** | Basic portfolio tracking with sample data | Done |
| **v2.0** | Multi-currency, multi-owner, Singapore/US market | Done |
| **v2.1** | Fixed Deposits module | Done |
| **v2.2** | Financial Planning & SRS module | Done |
| **v2.3** | Bank Savings, Insurance, Home Loans, Salary, Tax, Work Experience | Done |
| **v2.4** | JWT Authentication, Multi-tenant isolation, User Management | Done |
| **v2.5** | Audit Trail, Toast Notifications, Production Logging | Done |
| **v2.6** | 80%+ Test Coverage (228 tests), Integration Tests | Done |
| **v3.0** | Data import from CSV/Excel, automated price updates | Planned |
