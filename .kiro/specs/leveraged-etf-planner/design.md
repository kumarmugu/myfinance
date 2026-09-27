# Design — Leveraged ETF Allocation Planner

## 1. Context & approach

This feature is a new **per-user module** inside the existing MyFinance monolith (Spring Boot backend + React SPA). It follows the established module shape (`controller → service → repository → model`, tenant-scoped by `userId`, gated by a feature flag, `BigDecimal` money, audited) and reuses existing infrastructure rather than adding parallel stacks. No AWS services; scheduling is Spring `@Scheduled` (opt-in). Notifications are in-app first, email optional.

The design is deliberately phased so the **financial core (calculation engine)** lands first, fully unit-tested and independent of HTTP/DB, before any UI.

## 2. Reuse map (what already exists → how we use it)

| Existing capability | File(s) | How this feature reuses it |
|---|---|---|
| Tenant isolation | `security/TenantContext` | Stamp `userId` on create; `findByUserId...` finders; ownership checks on mutate. |
| Feature flags (CSV) | `AppUser.enabledFeatures`, `UserManagement` | New key `LEV_ETF`; empty CSV = all enabled; admin checklist entry; frontend nav gating via `useAuth().hasFeature`. |
| Market data (latest + FX) | `service/PriceFetchService` (Stooq/Yahoo v8 `/chart`) | **Extend** with `fetchDailyHistory(symbol, exchange, range)` using the same Yahoo `/chart` endpoint (`range`+`interval=1d`, parse `timestamp[]`+`indicators.quote[0]`+`adjclose`). No new provider, no keys. |
| FX conversion | `service/CurrencyConversionService` (user rates, latest by effectiveDate, direct→inverse→identity) | Convert ETF trading currency → base; surface the rate + timestamp used. |
| Portfolio aggregation | `HoldingService`, `TransactionService`, net-worth services | Portfolio-scope value (whole/account/manual); avoid double counting via existing holding model. |
| Audit trail | `service/AuditService.log(action, entity, id, details)` | Log strategy/plan CREATE/UPDATE/APPROVE/CANCEL/EXECUTE and manual reference-high edits. |
| Error handling | `config/GlobalExceptionHandler`, `ReferenceConstraintException` | RuntimeException→400; reference-blocked delete→409; per-field validation→400. |
| Frontend design system | `components/SearchableSelect`, `ToastContext`, Recharts, Tailwind, layout/nav | Cards, charts, selectors, toasts, responsive tables — no new UI library. |
| Export | existing CSV/Excel export utils (`utils/export/*`) | Trade-plan and backtest exports. |
| Broker import/dedupe | `IbkrSyncService` synthetic-id/`externalId` dedupe, CSV import framework | ETF holdings import + idempotent reconciliation. |

## 3. Gaps & risks (explicit)

1. **No historical price store / historical fetch today.** `PriceFetchService` returns only the latest quote. We add a `MarketDataBar` table and extend the service for daily history. The Yahoo v8 `/chart` endpoint already used *does* return history with a wider `range`, so this is an extension, not a new integration.
2. **Free feeds don't reliably cover indices.** Many index symbols (and CSE/niche tickers) return nothing. Mitigation: `benchmarkType = ETF_PROXY` (use a tracking ETF as the benchmark proxy), manual data entry, and explicit `dataQuality`/MISSING flags. We never fabricate data.
3. **No scheduler / no AWS.** Refresh is manual by default; an opt-in `@Scheduled` job (guarded by `app.levetf.scheduler.enabled`, default false) can refresh enrolled instruments. Idempotent + backoff.
4. **No email infra today.** Adding `spring-boot-starter-mail`; email is a no-op unless `app.mail.enabled=true` and `spring.mail.*` are set. In-app is the guaranteed channel.
5. **Leveraged-ETF path dependency.** Backtests must use the ETF's own price series; no synthetic multiple-of-index. A theoretical sim, if ever offered, is labelled distinct.
6. **`ddl-auto: update`.** All new tables are additive; the migration guard hook applies to each new `@Entity`.

## 4. Data model (new JPA entities — additive tables)

All money/price/qty/rate/percent fields are `BigDecimal`. All user-owned entities carry `Long userId`. Timestamps `LocalDateTime` (UTC) with `@PrePersist/@PreUpdate`. Enums stored `EnumType.STRING`.

- **`BenchmarkIndex`** (`lev_etf_benchmarks`): id, userId, symbol, name, exchange, provider, providerInstrumentId, currency, benchmarkType (PRICE_INDEX|TOTAL_RETURN_INDEX|ETF_PROXY), timezone, enabled, timestamps.
- **`LevEtfInstrument`** (`lev_etf_instruments`): id, userId, symbol, name, exchange, issuer, leverageMultiple (BigDecimal), leverageDirection (LONG|INVERSE), underlyingBenchmarkId, tradingCurrency, expenseRatio (nullable), resetFrequency (nullable), dataProvider, providerInstrumentId, metadataSource (USER|PROVIDER), enabled, timestamps.
- **`LevEtfStrategy`** (`lev_etf_strategies`): id, userId, name, description, benchmarkIndexId, etfInstrumentId, initialAllocationPercent, drawdownMultiplier, minimumAllocationPercent, maximumAllocationPercent, maximumAllocationEnabled, allocationMode (INITIAL_PLUS_HALF_DRAWDOWN|DRAWDOWN_ONLY_WITH_MIN|LADDER), ladderJson (nullable, for LADDER), referenceHighMode, referenceHighValue (nullable), referenceHighDate (nullable), referenceHighFrozen, portfolioScope (WHOLE|ACCOUNT|MANUAL), scopeAccountId (nullable), scopeManualValue (nullable), rebalanceTolerancePercent, rebalanceFrequency, tradingCurrency, ruleVersion (int, bumped on rule-affecting edits), enabled, archived, `@Version version`, timestamps.
- **`MarketDataBar`** (`lev_etf_market_bars`): id, userId, instrumentType (BENCHMARK|ETF), instrumentId, date, open, high, low, close, adjustedClose (nullable), volume (nullable), currency, provider, sourceTimestamp, ingestionTimestamp, dataQuality (OK|STALE|INVALID|MISSING). Unique (userId, instrumentType, instrumentId, date).
- **`LevEtfPositionSnapshot`** (`lev_etf_positions`): id, userId, strategyId, etfInstrumentId, snapshotDate, quantity, averageCost, marketPrice, marketValue, tradingCurrency, fxRate, baseCurrencyValue, source (MANUAL|BROKER_SYNC|IMPORT), externalId (nullable, for dedupe), syncTimestamp, timestamps.
- **`AllocationSnapshot`** (`lev_etf_allocation_snapshots`): id, userId, strategyId, ruleVersion, benchmarkPrice, referenceHigh, referenceHighDate, drawdownPercent, targetAllocationPercent, actualAllocationPercent, portfolioValueBase, targetEtfValueBase, currentEtfValueBase, rebalanceDifferenceBase, calculationTimestamp, benchmarkPriceTimestamp, etfPriceTimestamp, fxRate, dataQuality, blockedReason (nullable). Immutable once written.
- **`RebalancePlan`** (`lev_etf_rebalance_plans`): id, userId, strategyId, allocationSnapshotId, ruleVersion, action (BUY|REDUCE|SELL|NO_ACTION), quantity, estimatedPrice, estimatedAmount, currency, reason, status (DRAFT|PENDING_REVIEW|APPROVED|EXECUTED|PARTIALLY_EXECUTED|CANCELLED|EXPIRED), approvalTimestamp, executionTimestamp, executedQuantity, executedPrice, executedFees, brokerReference, notes, `@Version version`, timestamps.
- **`LevEtfAlertPref`** (`lev_etf_alert_prefs`): id, userId, strategyId (nullable=all), trigger (DRAWDOWN_THRESHOLD|TARGET_CHANGE|GAP_EXCEEDS|NEW_HIGH|STALE_DATA|PLAN_PENDING), threshold (nullable), inAppEnabled (default true), emailEnabled (default false), repeatPolicy (ONCE|REPEAT), timestamps.
- **`LevEtfAlertHistory`** (`lev_etf_alert_history`): id, userId, strategyId, trigger, threshold, message, dedupeKey (unique-ish per tenant+strategy+trigger+threshold+bucket), channelInApp, channelEmail, emailDeliveryStatus, read (default false), createdAt.
- **`Backtest`** (`lev_etf_backtests`) + **`BacktestResult`** stored as JSON blob column: id, userId, config (JSON: benchmark, etf, ruleVersion snapshot, dates, freq, fees/slippage/fx/cash), status (QUEUED|RUNNING|DONE|FAILED), resultsJson, warnings, createdAt, completedAt.

## 5. Calculation engine (Phase 1 core, pure & testable)

`service/levetf/AllocationCalculationService` — **no** Spring web / no JPA in the core methods; takes plain inputs, returns a result record. `BigDecimal` throughout, `MathContext`/scale fixed, `RoundingMode.HALF_UP`.

Core methods (pure):
- `drawdownPercent(referenceHigh, currentIndex)` → `max(0, (ref−cur)/ref×100)`; throws/returns "blocked" for null/≤0 ref or null/≤0 current.
- `targetAllocationPercent(drawdown, params)` where `params` = {mode, initial, multiplier, minimum, maximum, maxEnabled, ladder}. Implements the three modes; applies cap last when enabled.
- `referenceHigh(mode, bars, customStartDate, manualValue)` → computes from a list of *valid* bars (filters MISSING/INVALID/STALE/nonpositive); ROLLING_52_WEEK = last ~252 sessions.
- `RebalanceResult rebalance(portfolioValueBase, currentEtfValueBase, targetAllocationPercent, tolerancePercent, latestEtfPriceTrading, fxRate, rounding)` → target/diff/action/quantity/residual; returns NO_ACTION-with-reason (not a proposal) when portfolio ≤0/stale.

A `PricePoint`/`InstrumentBar` value object carries `dataQuality` + timestamp so the engine can refuse stale/invalid inputs and return a `blockedReason` rather than a number. **Never** substitutes zero.

`AllocationSnapshotService` (Spring, @Transactional) orchestrates: load bars/positions via repositories → call the pure engine → persist an immutable `AllocationSnapshot` (with the current `ruleVersion` and input timestamps) → optionally raise alerts.

## 6. Portfolio & rebalance integration

`LevEtfPortfolioService` resolves `portfolioScope`:
- WHOLE → existing net-worth/holdings aggregation in base currency.
- ACCOUNT → holdings for `scopeAccountId`.
- MANUAL → `scopeManualValue` (base currency).
Current ETF value = Σ `LevEtfPositionSnapshot` (latest per position) → base via `CurrencyConversionService`. Dedupe prevents double counting (positions carry `externalId`; broker sync reuses the existing idempotent pattern).

## 7. Market data service

`LevEtfMarketDataService`:
- `refreshLatest(instrument)` → existing `PriceFetchService.fetchLatestPrice`-style call, store a bar with today's date + `dataQuality=OK`.
- `refreshHistory(instrument, range)` → new `PriceFetchService.fetchDailyHistory(...)` (Yahoo `/chart`), upsert bars (unique key), mark gaps MISSING.
- staleness: a bar older than `app.levetf.stale-after-hours` (default 36h for daily) is STALE.
- `@Scheduled(cron=...)` `scheduledRefresh()` guarded by `app.levetf.scheduler.enabled` (default false); iterates enabled instruments across users; idempotent upserts; per-instrument try/catch + backoff; never throws out.

## 8. API surface (all under `/api/lev-etf`, JWT-guarded, `LEV_ETF` feature-gated, DTOs)

- `strategies` — GET (list), GET `/{id}`, POST, PUT `/{id}` (optimistic-locked), POST `/{id}/archive`.
- `benchmarks` — GET, POST, PUT `/{id}`, POST `/{id}/refresh`.
- `instruments` — GET, POST, PUT `/{id}`.
- `market-data` — GET `/{type}/{id}/history?range=`, GET `/{type}/{id}/status`, POST `/{type}/{id}/refresh`.
- `strategies/{id}/calculate` — POST (compute+persist snapshot), GET `/snapshots`.
- `strategies/{id}/positions` — GET, POST, PUT `/{posId}`, POST `/import`, POST `/sync`.
- `strategies/{id}/rebalance` — POST `/generate`, GET (list plans), POST `/plans/{planId}/approve`, POST `/plans/{planId}/cancel`, POST `/plans/{planId}/execution`.
- `alerts` — GET/PUT prefs, GET history, POST `/history/{id}/read`.
- `backtests` — POST, GET `/{id}` (status+results), GET (list), GET `/{id}/export`.
- `audit` — GET (reuse existing audit surface filtered to lev-etf entities).

DTOs on request+response; validation via `jakarta.validation`; errors via `GlobalExceptionHandler`; ownership checked in every handler.

## 9. Frontend

Route group `Investments > Leveraged ETF Planner`, gated by `hasFeature('LEV_ETF')`. Pages/tabs: Overview, Index Monitor, Strategy Configuration (wizard), Holdings & Rebalancing, Trade Plan & Log, Backtesting, and settings. Reuse layout, Recharts, `SearchableSelect`, toast, export. **Server is authoritative for all calculations**; the wizard's live allocation-table preview calls the same backend formula (or a documented client mirror clearly labelled "preview"). Data-freshness badge everywhere a figure appears. Full loading/empty/error/stale/permission-denied/success states; responsive cards + horizontally-scrollable tables on mobile.

## 10. Phase plan (matches the prompt)

- **Phase 1** — data model + repositories + pure calculation engine + rebalance math + **unit tests** (the mandated table + edge cases). No UI. *(this is where implementation begins)*
- **Phase 2** — extend `PriceFetchService` for history; `MarketDataBar` store + refresh (manual + opt-in scheduled); backend APIs + DTOs + snapshot/portfolio services.
- **Phase 3** — nav + feature flag; Overview, Index Monitor, Strategy wizard.
- **Phase 4** — Holdings & rebalancing, trade plan + execution log, audit surface.
- **Phase 5** — backtesting engine + reports; alerts (in-app + optional email) + exports.
- **Phase 6** — security review, full test pass, docs, regenerate results, restart prod.

After each phase: run backend (`./mvnw test`) + frontend (`npm test`) + `tsc`, keep coverage ≥80% backend, fix regressions, and report changed files, config vars, tests+results, and known limitations. No fabricated results; no "production ready" claim until tests and security checks pass.

## 11. New configuration variables

- `app.levetf.scheduler.enabled` (default `false`) — enable the `@Scheduled` refresh.
- `app.levetf.scheduler.cron` (default off-hours daily) — refresh schedule.
- `app.levetf.stale-after-hours` (default `36`) — when a daily bar is considered STALE.
- `app.mail.enabled` (default `false`) + standard `spring.mail.*` — optional email channel; no-op when unset.
- Reuses existing `app.price.provider` / `app.price.enabled`.
