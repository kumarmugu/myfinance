# Tasks — Leveraged ETF Allocation Planner

Incremental, test-after-each-phase. All new tables are **additive** (safe under `ddl-auto: update`); all new endpoints live under `/api/lev-etf/**` (no changes to existing endpoints). Money/qty/rate/percent are `BigDecimal`; every user-owned entity has `userId`.

## Phase 1 — Data model + calculation engine (no UI) ← START HERE

- [ ] 1. Enums (`model/enums/levetf/`): `BenchmarkType`, `LeverageDirection`, `AllocationMode`, `ReferenceHighMode`, `PortfolioScopeType`, `InstrumentType`, `DataQuality`, `RebalanceAction`, `RebalancePlanStatus`, `AlertTrigger`, `PositionSource`. _Req 2,3,4,5,6,8,9_
- [ ] 2. JPA entities under `model/` (additive tables, Lombok builder, `@PrePersist/@PreUpdate`, `BigDecimal`, `userId`, `@Version` on strategy+plan): `BenchmarkIndex`, `LevEtfInstrument`, `LevEtfStrategy`, `MarketDataBar`, `LevEtfPositionSnapshot`, `AllocationSnapshot`, `RebalancePlan`, `LevEtfAlertPref`, `LevEtfAlertHistory`, `Backtest`. _Req 2,3,4,6,7,8,9,10,12.3_
- [ ] 3. Repositories (`repository/`) with `findByUserId...` finders + unique constraints (e.g. bar unique on user+type+instrument+date; alert-history dedupeKey). _Req 1.2, 4.1, 9.4_
- [ ] 4. Pure `AllocationCalculationService` (`service/levetf/`): `drawdownPercent`, `targetAllocationPercent` (3 modes + caps), `referenceHigh` (4 modes), `rebalance(...)`. No Spring web/JPA in core methods; `BigDecimal`; refuse missing/stale/invalid/nonpositive with `blockedReason`; never substitute zero. _Req 5, 6_
- [ ] 5. Result records (`AllocationResult`, `RebalanceResult`, `InstrumentBar` VO with dataQuality+timestamp). _Req 5.6, 6_
- [ ] 6. **Unit tests** `AllocationCalculationServiceTest`: mandated table (0→10, 20→10, 30→15, 40→20, 50→25, 60→30, 80→40), cap-on caps at max, cap-off ignores max, LADDER lookup, index-above-high→0 drawdown, missing/zero/negative/stale price→blocked, zero/invalid portfolio→no proposal, missing FX→blocked, fractional/lot rounding + residual, BUY/REDUCE/NO_ACTION vs tolerance. _Req (acceptance) 5,6_
- [ ] 7. Run backend suite + coverage; report honestly.

## Phase 2 — Market data + backend APIs

- [ ] 8. Extend `PriceFetchService` with `fetchDailyHistory(symbol, exchange, range)` (Yahoo `/chart`, parse timestamps+quote+adjclose). Best-effort, never throws. Tests with captured JSON fixtures. _Req 4.2_
- [ ] 9. `LevEtfMarketDataService`: refreshLatest/refreshHistory → upsert `MarketDataBar`, mark MISSING/STALE; last-updated status. _Req 4.1,4.3,4.4,4.6_
- [ ] 10. Opt-in `@Scheduled` refresh (`app.levetf.scheduler.enabled`, default false; `@EnableScheduling` guarded), idempotent + backoff. _Req 4.5, 11.3_
- [ ] 11. `AllocationSnapshotService` + `LevEtfPortfolioService` (scope resolution, FX via `CurrencyConversionService`, dedupe). Persist immutable snapshots. _Req 5, 6, 7_
- [ ] 12. Controllers + DTOs + validation + ownership checks + `LEV_ETF` gating: strategies, benchmarks, instruments, market-data, calculate/snapshots, positions, rebalance, alerts prefs. _Req 1,2,3,4,6,7,8,9,12_
- [ ] 13. Feature flag `LEV_ETF`: add to `enabledFeatures` handling + admin checklist + create-user form (empty CSV = all enabled). _Req 1.4,1.5_
- [ ] 14. Backend integration + API-authorization tests (tenant isolation, 403 cross-tenant, optimistic-lock conflict, stale-data blocks proposal). Run suite + coverage.

## Phase 3 — Dashboard, Index Monitor, Strategy wizard

- [ ] 15. Frontend types + `api.ts` functions; nav entry `Investments > Leveraged ETF Planner` gated by `hasFeature('LEV_ETF')`.
- [ ] 16. Overview dashboard (summary cards, charts via Recharts, strategy + scope selectors, freshness badge).
- [ ] 17. Index Monitor (value/change, reference high, drawdown chart, methodology, data-quality, add/remove benchmark, manual high, custom start).
- [ ] 18. Strategy Configuration wizard (8 steps) with live allocation table 0–100%, inline validation, target-curve preview, rule-version warning. Server authoritative.
- [ ] 19. Frontend component tests where supported; `tsc` + `npm test`.

## Phase 4 — Holdings, rebalancing, trade history

- [ ] 20. Holdings page (manual entry, CSV import via existing framework, broker-sync where available, reconciliation/dedupe, P/L, base value, last sync).
- [ ] 21. Rebalance table + proposal generation; statuses; explicit approval (no order placement); recalc-on-stale guard; store snapshot+ruleVersion.
- [ ] 22. Trade Plan & Log (filters, approve/reject, record execution incl. partial, cancel, notes, CSV/Excel export); immutable audit via `AuditService`.
- [ ] 23. Tests + `tsc`.

## Phase 5 — Backtesting, alerts, exports

- [ ] 24. Backtesting engine (chronological, no look-ahead, real ETF series, path dependency, fees/slippage/fx/cash where configured; label theoretical sims distinct). Reports + warnings + export. Unit tests incl. no-future-info. _Req 10_
- [ ] 25. Alerts: in-app history (persisted, deduped) + toast; optional email via `spring-boot-starter-mail` guarded by `app.mail.enabled` (no-op when unset). Alert prefs UI + in-app notifications surface. _Req 9_
- [ ] 26. Tests + `tsc`.

## Phase 6 — Security, tests, docs, deploy

- [ ] 27. Security review: tenant ownership on every endpoint; no secret logging; guard tests.
- [ ] 28. Full backend + frontend suites green, coverage ≥80% backend; regenerate `test-results.json`.
- [ ] 29. Docs: `docs/ARCHITECTURE.md` (new module + endpoints + config vars), user-guide page.
- [ ] 30. Report changed files, migration notes (additive tables auto-created), config variables, tests executed + results, known limitations. Restart prod for verification. No "production ready" claim unless all checks pass.
