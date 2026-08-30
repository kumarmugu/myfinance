# MyFinance - Architecture Document

## 1. Overview

MyFinance is a self-hosted, **multi-tenant** personal finance and net-worth management application. A single deployment serves many users; each user sees only their own data (isolated by `userId`). It tracks investments, bank savings, fixed deposits, real estate, precious metals, retirement funds (CPF/SRS), insurance, home loans, salary/work history, tax, and budgets/expenses, and consolidates everything into a configurable net worth.

**Currencies are user-created** (no fixed enum list, no external FX feed). Every record preserves its **original currency + amount** as the source of truth; a per-user **base currency** (default SGD) drives consolidation and a per-user **display-currency** toggle re-derives shown values via the user's own FX rates. Almost every module can be enabled or disabled **per user** via feature flags.

> **Roles.** `USER` accounts own financial data. `ADMIN` accounts manage users, per-user feature flags and the audit trail — admins do **not** own financial data and the dashboard/asset pages are user-facing only.

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
INDEX_FUND, MUTUAL_FUND, GROWTH_EQUITY, DIVIDEND_EQUITY, LEVERAGED_ETF,
MONEY_MARKET, FIXED_DEPOSIT, SAVINGS, CRYPTO, GOLD, BOND, REIT, COMMODITY,
INSURANCE, PENSION, OTHER
```
(Source of truth: `frontend/src/types/index.ts` `ASSET_TYPE_LABELS`.)

### TransactionType
```
BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL
```

### Currency
**Not a fixed enum.** Currencies are user-created and stored per record.
- Older entities use a `Currency` enum with a broad code set (SGD, USD, EUR, LKR, INR, GBP, AUD, JPY, CNY, MYR, THB, HKD, NZD, CHF, CAD): Account, BankSavings, Dividend, Holding, Asset, Transaction, SoldPosition, AccountDeposit, HomeLoan, InsurancePolicy.
- Newer/String-based entities store the currency as a free-text ISO code: Property, PreciousMetal, GenericFixedDeposit, SalaryRecord, TaxRecord, RetirementFundEntry, FixedDeposit (SL).
- Base-only by design (no currency field): CPF/SRS and Budget.

### FDStatus
```
ACTIVE, MATURED, RENEWED, CLOSED, REQUIRES_UPDATE
```

### OwnerRelationship
```
SELF, SPOUSE, SON, DAUGHTER, FATHER, MOTHER, BROTHER, SISTER
```

### InvestmentPurpose
```
LONG_TERM, TRADING, DIVIDEND_REINVESTMENT, SRS, RETIREMENT, SHORT_TERM
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

Routes are defined in `frontend/src/App.tsx`. User routes render for non-admins; admin routes render for admins (each guarded inline).

```
User (non-admin):
/                 → Dashboard              /planning     → Allocation & Net Worth
/portfolio        → Portfolio              /budget       → Budget & Expenses
/transactions     → Transactions           /reports      → Reports
/dividends        → Dividends              /accounts     → Brokers & Owners
/crypto           → Crypto                 /assets       → Asset Catalog
/deposits         → Cash Flows             /fx-rates     → FX Rates & Currencies
/fixed-deposits   → Fixed Deposits         /net-worth-config → Net Worth Config
/bank-savings     → Bank Savings           /guide        → User Guide (interactive)
/insurance        → Life Insurance
/home-loans       → Home Loans             Admin:
/properties       → Real Estate            /admin/users  → User Management
/precious-metals  → Gold & Silver          /admin/audit  → Audit Trail
/salary           → Salary                 /test-results → Test Results
/work-experience  → Work Experience        /docs         → Documentation (Swagger)
/tax              → Tax Records            /guide        → User Guide
/srs-cpf          → SRS & CPF
```

Feature-gated user routes (hidden when the feature flag is off): PORTFOLIO, DIVIDENDS, CRYPTO, CASH_FLOWS, BANK_SAVINGS, FIXED_DEPOSITS, REAL_ESTATE, PRECIOUS_METALS, SALARY, WORK_EXPERIENCE, SRS_CPF, TAX, INSURANCE, HOME_LOANS, BUDGET, REPORTS. Dashboard, Planning, and the Configuration screens (Brokers & Owners, Asset Catalog, FX Rates, Net Worth Config) are always available.

### User Guide
`/guide` is an interactive, searchable, content-driven guide (`frontend/src/pages/UserGuide.tsx` + `userGuideContent.ts`). It is organised by the real setup order (currencies/FX → owners & accounts → asset catalog → modules → net worth config → dashboard/reports), filters its pages by the same feature flags as the nav, tracks a local "Getting Started" progress checklist, and supports screenshots/videos plus per-page contextual help.

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

The application handles multiple currencies with the following approach (full detail in `docs/DESIGN.md` § Currency Handling):

1. **Original currency + amount** — the source of truth. Stored on every record and never overwritten by conversion.
2. **Base currency** — per user (`app_users.base_currency`, null → SGD). Used only for consolidation (Net Worth, summary totals).
3. **Display currency** — per user (`app_users.display_currencies`, null → `SGD,USD`), a presentational UI toggle that re-derives shown values via FX.
4. **Conversion** — done at read-time by `CurrencyConversionService` using the user's own `currency_rates` rows (latest by `effective_date`; direct → inverse → identity fallback). **No hardcoded FX** anywhere, on client or server. **No external rate feed** — rates are user-maintained.

### Currency source per entity
- **`Currency` enum:** Account, BankSavings, Dividend, Holding, Asset, Transaction, SoldPosition, AccountDeposit, HomeLoan, InsurancePolicy.
- **String ISO code:** Property, PreciousMetal, GenericFixedDeposit, SalaryRecord, TaxRecord, RetirementFundEntry, FixedDeposit (SL). Nullable → treated as the user's base currency.
- **Base-only (no currency field):** CPF/SRS, Budget.

### Broker account vs investment currency
`Account.currency` (settlement/broker currency), `Asset.currency`/`Holding.currency` (the instrument's own currency), and `Transaction.currency` (the settlement currency) are distinct and all preserved. Example: buying a EUR fund through a USD broker → `Holding.currency = EUR`, `Transaction.currency = USD`.

---

## 10. Multi-Tenancy & Multi-Owner Strategy

Two independent layers of separation exist:

**Tenant isolation (security boundary).** Every user-owned entity carries a `Long userId`. All reads use `findByUserId...` finders and all writes stamp `tenantContext.getCurrentUserId()`. A user can never see or mutate another user's data. This is guarded by `MultiTenantIsolationTest` and is treated as a top-priority security concern.

**Owners (within a tenant).** Inside a single user's data, `Owner` records (relationship SELF, SPOUSE, SON, DAUGHTER, FATHER, MOTHER, BROTHER, SISTER) let that user attribute finances to different people (e.g. self and spouse). Holdings, transactions, dividends, snapshots and most modules reference an owner, and pages can filter by owner or show a consolidated view. Owners are a data-modelling convenience; they are **not** a security boundary — tenant isolation is.

An owner or account cannot be deleted while records still reference it (`ReferenceConstraintException` → HTTP 409 with a `references` list).

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
| **v2.7** | Per-user base/display currency, user-created currencies, no hardcoded FX | Done |
| **v2.8** | Interactive in-app User Guide + contextual help; docs realigned to implementation | Done |
| **v3.0** | Data import from CSV/Excel, automated price updates | Planned |
