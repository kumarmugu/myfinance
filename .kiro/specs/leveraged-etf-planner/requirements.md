# Requirements — Leveraged ETF Allocation Planner

## Introduction

MyFinance tracks investments (assets, holdings, transactions, dividends) and consolidates a per-user net worth. This feature adds a **Leveraged ETF Allocation Planner**: a per-user, multi-tenant planning and monitoring tool that lets a user configure underlying indices and leveraged ETFs, monitor index drawdowns, define drawdown-driven allocation rules, track actual ETF holdings, calculate target positions, generate rebalance *proposals*, record decisions, and backtest the rules against history.

This is a **planning and monitoring** tool only. It **never** places broker orders automatically. Every trade proposal requires explicit user review and approval, and execution is recorded manually (or via an existing, explicit broker workflow only if the user confirms). The feature does not promise returns, guarantee outcomes, or characterize leveraged ETFs as suitable investments; it surfaces neutral, data-driven figures with risk caveats.

It follows the existing module pattern: per-user isolation by `userId` via `TenantContext`, a per-user feature flag in `AppUser.enabledFeatures`, `BigDecimal` money, original-currency-preserving records with base-currency consolidation via the user's own FX rates, the existing audit trail, and the existing React/Tailwind/Recharts design system.

## Decisions & platform constraints (confirmed with the user)

- **Backend stays Java 17 / Spring Boot 3.2.5** (JPA/Hibernate, H2 dev / PostgreSQL prod). No language or framework change.
- **No AWS services.** Scheduled market-data refresh uses **Spring `@Scheduled`**, opt-in and disabled by default via a config flag. Manual refresh is always available. No EventBridge/Lambda/ECS.
- **Notifications: in-app (web) is the primary, always-on channel.** A persisted, deduplicated per-user alert history is surfaced in the app and live pop-ups reuse the existing toast system. **Email is an optional, per-preference secondary channel** delivered only when SMTP is configured by the operator (env-driven); when unconfigured it is a logged no-op and never fails the alert. No third-party notification provider.

## Glossary

- **Benchmark index** — the underlying index (or an ETF proxy for it) whose drawdown drives allocation. Separate instrument from the leveraged ETF.
- **Leveraged ETF** — the traded instrument (e.g. 2x/3x long or inverse) tracking the benchmark. Its own price series; **never** used to compute the benchmark drawdown.
- **Reference high** — the peak benchmark value the drawdown is measured from (ALL_TIME, ROLLING_52_WEEK, CUSTOM_START_DATE, or MANUAL).
- **Drawdown** — `max(0, (referenceHigh − currentIndex) / referenceHigh × 100)`.
- **Target allocation %** — the portfolio weight the strategy wants in the ETF, derived from drawdown by the selected allocation rule.
- **Rebalance proposal** — a computed BUY / REDUCE / NO_ACTION suggestion; advisory only, requires approval.
- **Portfolio scope** — which value the target % is applied to: whole investment portfolio, one account/broker, or a manual amount.
- **Data freshness** — the last successful market-data update time; stale data blocks proposals and is shown in the UI.

## Requirements

### Requirement 1 — Multi-tenant isolation & feature flag

**User Story:** As a user, I want my planner data private to me and toggleable, so it stays isolated and I only see it when enabled.

#### Acceptance Criteria
1. WHEN any planner record (strategy, benchmark mapping, ETF instrument mapping, position snapshot, allocation snapshot, rebalance plan, alert preference/history, backtest) is created THEN the system SHALL stamp it with the authenticated user's `userId` from `TenantContext` and never trust a `userId` in the request body.
2. WHEN a user reads or lists planner records THEN the system SHALL return only records belonging to that user (`findByUserId...` finders, never `findAll()`).
3. IF a user attempts to read, update, delete, approve, execute, export, or recalculate a record owned by another user THEN the system SHALL NOT expose or modify it (verify ownership, return 403/404 per existing convention).
4. THE feature SHALL be represented by a key `LEV_ETF` in the existing `AppUser.enabledFeatures` CSV (no new mechanism); empty CSV still means all features enabled.
5. WHEN the feature is disabled for a user THEN the frontend SHALL hide the navigation and pages, and gated endpoints SHALL reject the request.

### Requirement 2 — Strategy configuration

**User Story:** As a user, I want to configure a strategy (index, ETF, allocation rule, caps, reference-high method, tolerance, scope), so the planner reflects my plan.

#### Acceptance Criteria
1. THE system SHALL store a Strategy with: `userId`, name, description, `benchmarkIndexId`, `etfInstrumentId`, `initialAllocationPercent` (default 10), `drawdownMultiplier` (default 0.5), `minimumAllocationPercent` (default 10), `maximumAllocationPercent` (e.g. 50), `maximumAllocationEnabled`, `allocationMode`, `referenceHighMode` (ALL_TIME | ROLLING_52_WEEK | MANUAL | CUSTOM_START_DATE), `referenceHighValue`, `referenceHighDate`, `referenceHighFrozen`, `portfolioScope`, `rebalanceTolerancePercent`, `rebalanceFrequency`, `tradingCurrency`, `enabled`, timestamps, and a `version` for optimistic concurrency.
2. THE system SHALL validate that `minimumAllocationPercent` ≤ `maximumAllocationPercent` (when the cap is enabled) and that percentages are within [0, 100]; invalid input SHALL be rejected with a per-field 400.
3. WHEN a strategy rule is changed THEN the system SHALL version it and SHALL NOT overwrite or retroactively alter existing allocation snapshots or previously-approved rebalance plans.
4. WHEN a strategy is edited concurrently THEN the system SHALL use optimistic locking (`@Version`) and reject a stale update.
5. THE system SHALL support enabling/disabling and archiving a strategy without deleting its historical snapshots.

### Requirement 3 — Benchmark index & ETF instrument metadata

**User Story:** As a user, I want to register indices and leveraged ETFs with accurate metadata, so calculations use the right instruments.

#### Acceptance Criteria
1. THE system SHALL store a Benchmark with: `userId`, symbol, name, exchange, provider, `providerInstrumentId`, currency, `benchmarkType` (PRICE_INDEX | TOTAL_RETURN_INDEX | ETF_PROXY), timezone, enabled.
2. THE system SHALL store an ETF instrument with: `userId`, symbol, name, exchange, issuer, `leverageMultiple`, `leverageDirection` (LONG | INVERSE), `underlyingBenchmarkId`, `tradingCurrency`, `expenseRatio` (optional), `resetFrequency` (optional, e.g. DAILY), `dataProvider`, `providerInstrumentId`, `metadataSource`, enabled.
3. THE system SHALL NOT infer missing product characteristics; unknown fields SHALL be stored/displayed as unknown, and `metadataSource` SHALL record where metadata came from (USER, PROVIDER).
4. THE system SHALL NOT assume every leveraged ETF is a daily-reset long ETF; direction, multiple, reset frequency, and underlying benchmark are explicit fields.
5. WHEN an ETF is mapped to a benchmark THEN the two SHALL remain distinct instruments with independent price series.

### Requirement 4 — Market data history & provider limits

**User Story:** As a user, I want current and historical prices for my index and ETF, so drawdown, reference high, and backtests are accurate — and I want to see when data is stale.

#### Acceptance Criteria
1. THE system SHALL persist daily market-data bars: `instrumentRef` (benchmark or ETF), date, open, high, low, close, adjustedClose (nullable), volume (nullable), currency, provider, sourceTimestamp, ingestionTimestamp, and `dataQuality` (OK | STALE | INVALID | MISSING).
2. THE system SHALL reuse and EXTEND the existing `PriceFetchService` (Stooq/Yahoo) to fetch (a) the latest quote and (b) daily historical OHLCV via the Yahoo v8 `/chart` endpoint with a configurable range; it SHALL NOT hardcode API keys or invent endpoints.
3. IF the provider does not cover a symbol (e.g. many indices, CSE tickers) THEN the system SHALL record MISSING/unknown and allow the user to maintain data manually and/or use an ETF_PROXY benchmark; it SHALL NOT fabricate data.
4. THE system SHALL exclude missing, stale, invalid, or nonpositive prices from calculations and SHALL NEVER substitute zero for missing data.
5. THE system SHALL support a manual refresh action and an OPT-IN scheduled refresh via Spring `@Scheduled`, disabled by default via config (`app.levetf.scheduler.enabled=false`); scheduled processing SHALL be idempotent and retry-safe with backoff.
6. THE system SHALL surface last-successful-update time, provider name, and stale-data warnings, and SHALL document whether the reference high uses adjusted or unadjusted closes (kept consistent between live monitoring and backtests).

### Requirement 5 — Allocation calculation engine

**User Story:** As a user, I want a correct, transparent, testable allocation rule, so my target weight follows my configured strategy.

#### Acceptance Criteria
1. THE system SHALL implement an independently testable calculation service (no financial logic in controllers or frontend) computing, with `BigDecimal`:
   - `drawdownPercent = max(0, (referenceHigh − currentIndex) / referenceHigh × 100)`
   - `rawAllocationPercent = drawdownPercent × drawdownMultiplier`
   - `targetAllocationPercent = max(minimumAllocationPercent, rawAllocationPercent)`, then `min(maximumAllocationPercent, …)` when the cap is enabled.
2. THE default strategy (initial 10, multiplier 0.5, min 10, cap 50) SHALL produce: 0%→10, 20%→10, 30%→15, 40%→20, 50%→25, 60%→30, 80%→40 (and cap at 50 beyond).
3. THE system SHALL support at least these versioned allocation modes and SHALL NOT silently mix them: (a) INITIAL_PLUS_HALF_DRAWDOWN = `max(initial, drawdown × multiplier)`; (b) DRAWDOWN_ONLY_WITH_MIN = `max(minimum, drawdown × multiplier)`; (c) LADDER = a user-defined drawdown-threshold → allocation table. The selected mode and its formula SHALL be shown in the UI and recorded in each snapshot with the rule version.
4. THE reference high SHALL be computed per mode: ALL_TIME (max valid close in history), ROLLING_52_WEEK (max valid close over ~252 sessions), CUSTOM_START_DATE (max valid close since a date), MANUAL (user value+date, editable, every change audited).
5. WHEN a new index high occurs THEN the reference high SHALL update per the configured mode UNLESS `referenceHighFrozen` is set, in which case it holds until the user resets it.
6. IF the benchmark price is missing, stale, invalid, or nonpositive THEN the system SHALL NOT produce a target allocation and SHALL flag the reason and last-good timestamp.

### Requirement 6 — Portfolio value & rebalance proposals

**User Story:** As a user, I want target vs actual ETF value and a clear BUY/REDUCE/NO_ACTION proposal, so I know what to do — without anything being executed for me.

#### Acceptance Criteria
1. THE system SHALL compute portfolio value using the configured `portfolioScope` (whole portfolio | one account/broker | manual amount), reusing existing portfolio aggregation and `CurrencyConversionService`, and SHALL prevent double-counting a position across manual entry and broker sync.
2. THE system SHALL compute: `targetEtfValueBase = portfolioValueBase × targetAllocationPercent / 100`; `currentEtfValueBase = Σ ETF positions → base via FX`; `rebalanceDifferenceBase = target − current`; `actualAllocationPercent = current / portfolioValue × 100`.
3. IF portfolio value is zero, negative, unavailable, or stale THEN the system SHALL NOT generate a proposal.
4. THE system SHALL classify: positive difference → BUY; negative → REDUCE/SELL; within `rebalanceTolerancePercent` → NO_ACTION.
5. THE system SHALL compute `estimatedQuantity = |differenceInTradingCurrency| / latestValidEtfPrice`, respecting fractional-share support, minimum order size, quantity precision, and lot size **when known** (else marked unknown, never assumed), and SHALL display the residual allocation gap after rounding.
6. THE system SHALL display: portfolio base value; ETF quantity & average cost; latest ETF price + timestamp; ETF value in trading & base currency; target vs actual weight; target value; proposed amount & quantity; estimated residual; FX rate + timestamp; and fees/taxes when available (else clearly stated as excluded).
7. THE system SHALL use the benchmark (not the ETF) price series to determine drawdown.

### Requirement 7 — Holdings tracking & reconciliation

**User Story:** As a user, I want to record/track my leveraged ETF holdings, so actual allocation is accurate.

#### Acceptance Criteria
1. THE system SHALL support manual position entry, CSV import via the existing import framework, and existing broker-sync where available, storing position snapshots (strategyId, etfInstrumentId, date, quantity, avg cost, market price, market value, trading currency, FX rate, base value, source, sync timestamp).
2. THE system SHALL detect duplicates and be idempotent on re-import/re-sync (reuse the existing external-id/dedupe pattern) so the same position is not counted twice.
3. THE system SHALL show quantity, cost basis, current price, market value, unrealized P/L, trading & base currency, account/broker, and last sync time.

### Requirement 8 — Rebalance plans, decisions & audit

**User Story:** As a user, I want to review, approve, and record execution of proposals with a full history, so decisions are deliberate and traceable.

#### Acceptance Criteria
1. THE system SHALL store rebalance plans with: proposed action, quantity, estimated price, estimated amount, currency, reason, strategyId, `allocationSnapshotId`, `ruleVersion`, status, user approval timestamp, execution confirmation timestamp, actual execution details, notes, and audit timestamps.
2. THE status SHALL be one of DRAFT, PENDING_REVIEW, APPROVED, EXECUTED, PARTIALLY_EXECUTED, CANCELLED, EXPIRED.
3. Approval SHALL require explicit user confirmation and SHALL NOT place a broker order; execution is recorded manually (or via an existing explicit broker workflow only if present and the user confirms).
4. THE system SHALL store the exact allocation snapshot + rule version used to generate each proposal, so later rule edits do not change historical proposals.
5. IF market data or FX becomes stale after a proposal is created THEN the system SHALL warn and require recalculation before approval.
6. THE system SHALL maintain an immutable audit trail (via the existing `AuditService`) of material changes, approvals, cancellations, and execution confirmations, recording who and when, and SHALL support CSV/Excel export via existing export services.

### Requirement 9 — Alerts & in-app notifications

**User Story:** As a user, I want to be notified in the app (and optionally by email) when key thresholds are crossed, without spam.

#### Acceptance Criteria
1. THE system SHALL support user-configurable alerts for: index drawdown crossing 10/20/30/40% and custom thresholds; target allocation changing; allocation gap exceeding a configured amount; new reference high; market data becoming stale; and a rebalance plan awaiting review.
2. THE **in-app (web) channel SHALL be the primary, always-on channel**: alerts SHALL be persisted to a per-user, deduplicated `LevEtfAlertHistory` surfaced in the app, and live events SHALL reuse the existing toast system.
3. Email SHALL be an **optional per-preference channel** delivered only when SMTP is configured by the operator (env-driven, e.g. `spring.mail.*` + `app.mail.enabled`); when unconfigured it SHALL be a logged no-op and SHALL NOT fail the alert or the triggering calculation. No third-party notification provider SHALL be introduced.
4. THE system SHALL deduplicate notifications: it SHALL NOT re-send the same threshold alert unless the index exits and re-enters the threshold, or the user configures a repeat policy (dedup key per tenant+strategy+trigger+threshold+bucket).

### Requirement 10 — Backtesting & historical analysis

**User Story:** As a user, I want to backtest a strategy on real history with honest caveats, so I can study it without being misled.

#### Acceptance Criteria
1. THE system SHALL simulate a strategy chronologically over a configured date range using historical benchmark and ETF prices, computing the reference high at each date from **only** information available on that date (no look-ahead).
2. THE system SHALL let the user configure: benchmark, ETF, rule + version, initial portfolio value, start/end dates, rebalance frequency, reference-high method, caps/tolerances, and fees/slippage/FX/cash assumptions where supported.
3. THE system SHALL use the **actual historical ETF price series** and SHALL NOT fabricate pre-inception ETF prices or treat a multiple of cumulative index returns as actual ETF performance; a theoretical leveraged-index simulation, if offered, SHALL be clearly labelled as distinct from an actual ETF backtest.
4. THE system SHALL report: equity curve; benchmark & buy-and-hold comparison; total & annualized return; max drawdown; volatility; Sharpe ratio only when the risk-free rate/assumptions are defined; turnover/trade count/estimated costs; max ETF allocation; time at each allocation; cash requirements and unexecuted amounts.
5. THE system SHALL display explicit warnings that historical performance is not a prediction and that the backtest may omit costs or use incomplete data, and SHALL support exporting assumptions, input data references, rule version, results, and charts.

### Requirement 11 — Security, reliability & data integrity

**User Story:** As a user and operator, I want the feature to be safe, reliable, and never show stale numbers as current.

#### Acceptance Criteria
1. THE system SHALL follow existing JWT auth/authorization and validate tenant ownership on every strategy, instrument mapping, position, calculation, plan, alert, backtest, and export.
2. THE system SHALL NOT log secrets, API keys, credentials, or unnecessary personal financial data.
3. THE system SHALL use DB transactions where appropriate, idempotent scheduled processing, retry-safe jobs, and optimistic locking for concurrent strategy edits, and SHALL degrade gracefully on provider outages, missing prices, FX failures, and partial broker sync.
4. THE system SHALL NOT display stale calculations as current; data freshness SHALL be visible everywhere a figure is shown, and monetary/quantity/FX/percent values SHALL use `BigDecimal` (never binary floating point). Timestamps SHALL be stored in UTC and displayed in the user's timezone.

### Requirement 12 — API & migrations compatibility

**User Story:** As a maintainer, I want the feature added without breaking existing APIs or the prod database.

#### Acceptance Criteria
1. ALL new endpoints SHALL live under a new namespace (`/api/lev-etf/**`) following existing REST conventions (DTOs, validation, pagination, standardized errors via `GlobalExceptionHandler`, auth middleware) and SHALL NOT change or break existing endpoints.
2. THE system SHALL NOT expose internal JPA entities directly as API responses where the existing pattern uses DTOs; it MAY return entities only where the existing modules already do so.
3. ALL schema changes SHALL be **additive** (new tables/columns only) and safe under `ddl-auto: update` against the existing production database — no renames, drops, or lossy type changes; new NOT NULL columns only on new tables.
