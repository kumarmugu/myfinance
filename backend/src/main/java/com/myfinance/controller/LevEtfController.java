package com.myfinance.controller;

import com.myfinance.model.*;
import com.myfinance.model.enums.levetf.InstrumentType;
import com.myfinance.repository.*;
import com.myfinance.security.TenantContext;
import com.myfinance.service.CurrencyConversionService;
import com.myfinance.service.levetf.AllocationCalculationService;
import com.myfinance.service.levetf.LevEtfMarketDataService;
import com.myfinance.service.levetf.LevEtfPlannerService;
import com.myfinance.service.levetf.LevEtfAlertService;
import com.myfinance.service.levetf.RebalancePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the Leveraged ETF Allocation Planner, all under {@code /api/lev-etf/**}. Every read
 * and mutation is scoped to the authenticated user (tenant isolation): creates stamp {@code userId} from
 * {@link TenantContext} and never trust a body userId; updates/deletes load-verify-mutate by ownership.
 * Calculations are server-authoritative and go through the pure engine + planner services.
 */
@Slf4j
@RestController
@RequestMapping("/api/lev-etf")
@RequiredArgsConstructor
public class LevEtfController {

    private final TenantContext tenantContext;
    private final BenchmarkIndexRepository benchmarkRepository;
    private final LevEtfInstrumentRepository instrumentRepository;
    private final LevEtfStrategyRepository strategyRepository;
    private final LevEtfPositionSnapshotRepository positionRepository;
    private final AllocationSnapshotRepository allocationSnapshotRepository;
    private final MarketDataBarRepository barRepository;
    private final LevEtfAlertPrefRepository alertPrefRepository;
    private final LevEtfMarketDataService marketDataService;
    private final LevEtfPlannerService plannerService;
    private final RebalancePlanService rebalanceService;
    private final LevEtfAlertService alertService;
    private final com.myfinance.service.levetf.LevEtfBacktestService backtestService;
    private final AllocationCalculationService engine;
    private final CurrencyConversionService fx;

    private Long uid() {
        Long id = tenantContext.getCurrentUserId();
        if (id == null) throw new RuntimeException("Not authenticated");
        return id;
    }

    private <T> T ownedOrThrow(T entity, Long ownerId, String name) {
        if (entity == null || ownerId == null || !ownerId.equals(uid())) {
            throw new RuntimeException(name + " not found");
        }
        return entity;
    }

    // ─────────────────────────── benchmarks ───────────────────────────

    @GetMapping("/benchmarks")
    public List<BenchmarkIndex> listBenchmarks() { return benchmarkRepository.findByUserId(uid()); }

    @PostMapping("/benchmarks")
    public ResponseEntity<BenchmarkIndex> createBenchmark(@RequestBody BenchmarkIndex b) {
        b.setUserId(uid());
        b.setId(null);
        if (b.getBenchmarkType() == null) b.setBenchmarkType(com.myfinance.model.enums.levetf.BenchmarkType.PRICE_INDEX);
        return ResponseEntity.status(HttpStatus.CREATED).body(benchmarkRepository.save(b));
    }

    @PutMapping("/benchmarks/{id}")
    public BenchmarkIndex updateBenchmark(@PathVariable Long id, @RequestBody BenchmarkIndex u) {
        BenchmarkIndex e = benchmarkRepository.findById(id).orElse(null);
        ownedOrThrow(e, e == null ? null : e.getUserId(), "Benchmark");
        e.setSymbol(u.getSymbol());
        e.setName(u.getName());
        e.setExchange(u.getExchange());
        e.setProvider(u.getProvider());
        e.setCurrency(u.getCurrency());
        if (u.getBenchmarkType() != null) e.setBenchmarkType(u.getBenchmarkType());
        e.setTimezone(u.getTimezone());
        if (u.getEnabled() != null) e.setEnabled(u.getEnabled());
        return benchmarkRepository.save(e);
    }

    @DeleteMapping("/benchmarks/{id}")
    public ResponseEntity<Void> deleteBenchmark(@PathVariable Long id) {
        BenchmarkIndex e = benchmarkRepository.findById(id).orElse(null);
        ownedOrThrow(e, e == null ? null : e.getUserId(), "Benchmark");
        benchmarkRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── instruments (ETFs) ───────────────────────────

    @GetMapping("/instruments")
    public List<LevEtfInstrument> listInstruments() { return instrumentRepository.findByUserId(uid()); }

    @PostMapping("/instruments")
    public ResponseEntity<LevEtfInstrument> createInstrument(@RequestBody LevEtfInstrument i) {
        i.setUserId(uid());
        i.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(instrumentRepository.save(i));
    }

    @PutMapping("/instruments/{id}")
    public LevEtfInstrument updateInstrument(@PathVariable Long id, @RequestBody LevEtfInstrument u) {
        LevEtfInstrument e = instrumentRepository.findById(id).orElse(null);
        ownedOrThrow(e, e == null ? null : e.getUserId(), "ETF instrument");
        e.setSymbol(u.getSymbol());
        e.setName(u.getName());
        e.setExchange(u.getExchange());
        e.setIssuer(u.getIssuer());
        e.setLeverageMultiple(u.getLeverageMultiple());
        e.setLeverageDirection(u.getLeverageDirection());
        e.setUnderlyingBenchmarkId(u.getUnderlyingBenchmarkId());
        e.setTradingCurrency(u.getTradingCurrency());
        e.setExpenseRatio(u.getExpenseRatio());
        e.setResetFrequency(u.getResetFrequency());
        if (u.getEnabled() != null) e.setEnabled(u.getEnabled());
        return instrumentRepository.save(e);
    }

    @DeleteMapping("/instruments/{id}")
    public ResponseEntity<Void> deleteInstrument(@PathVariable Long id) {
        LevEtfInstrument e = instrumentRepository.findById(id).orElse(null);
        ownedOrThrow(e, e == null ? null : e.getUserId(), "ETF instrument");
        instrumentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── strategies ───────────────────────────

    @GetMapping("/strategies")
    public List<LevEtfStrategy> listStrategies() {
        return strategyRepository.findByUserIdAndArchivedFalseOrderByCreatedAtDesc(uid());
    }

    @GetMapping("/strategies/{id}")
    public LevEtfStrategy getStrategy(@PathVariable Long id) {
        return plannerService.requireStrategy(uid(), id);
    }

    @PostMapping("/strategies")
    public ResponseEntity<LevEtfStrategy> createStrategy(@RequestBody LevEtfStrategy s) {
        s.setUserId(uid());
        s.setId(null);
        s.setRuleVersion(1);
        return ResponseEntity.status(HttpStatus.CREATED).body(strategyRepository.save(s));
    }

    @PutMapping("/strategies/{id}")
    public LevEtfStrategy updateStrategy(@PathVariable Long id, @RequestBody LevEtfStrategy u) {
        LevEtfStrategy e = plannerService.requireStrategy(uid(), id);
        // Bump ruleVersion when a rule-affecting field changes, so historical snapshots keep their rule.
        boolean ruleChanged = changed(e.getInitialAllocationPercent(), u.getInitialAllocationPercent())
                || changed(e.getDrawdownMultiplier(), u.getDrawdownMultiplier())
                || changed(e.getMinimumAllocationPercent(), u.getMinimumAllocationPercent())
                || changed(e.getMaximumAllocationPercent(), u.getMaximumAllocationPercent())
                || e.getAllocationMode() != u.getAllocationMode()
                || !safeEq(e.getLadderJson(), u.getLadderJson());
        e.setName(u.getName());
        e.setDescription(u.getDescription());
        e.setBenchmarkIndexId(u.getBenchmarkIndexId());
        e.setEtfInstrumentId(u.getEtfInstrumentId());
        e.setInitialAllocationPercent(u.getInitialAllocationPercent());
        e.setDrawdownMultiplier(u.getDrawdownMultiplier());
        e.setMinimumAllocationPercent(u.getMinimumAllocationPercent());
        e.setMaximumAllocationPercent(u.getMaximumAllocationPercent());
        if (u.getMaximumAllocationEnabled() != null) e.setMaximumAllocationEnabled(u.getMaximumAllocationEnabled());
        e.setAllocationMode(u.getAllocationMode());
        e.setLadderJson(u.getLadderJson());
        e.setReferenceHighMode(u.getReferenceHighMode());
        e.setReferenceHighValue(u.getReferenceHighValue());
        e.setReferenceHighDate(u.getReferenceHighDate());
        if (u.getReferenceHighFrozen() != null) e.setReferenceHighFrozen(u.getReferenceHighFrozen());
        e.setPortfolioScope(u.getPortfolioScope());
        e.setScopeAccountId(u.getScopeAccountId());
        e.setScopeManualValue(u.getScopeManualValue());
        e.setRebalanceTolerancePercent(u.getRebalanceTolerancePercent());
        e.setRebalanceFrequency(u.getRebalanceFrequency());
        e.setTradingCurrency(u.getTradingCurrency());
        if (u.getEnabled() != null) e.setEnabled(u.getEnabled());
        if (ruleChanged) e.setRuleVersion((e.getRuleVersion() == null ? 1 : e.getRuleVersion()) + 1);
        return strategyRepository.save(e);
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable Long id) {
        LevEtfStrategy e = plannerService.requireStrategy(uid(), id);
        e.setArchived(true);
        strategyRepository.save(e);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── market data ───────────────────────────

    @GetMapping("/market-data/{type}/{instrumentId}")
    public List<MarketDataBar> history(@PathVariable InstrumentType type, @PathVariable Long instrumentId) {
        return marketDataService.history(uid(), type, instrumentId);
    }

    @PostMapping("/market-data/benchmark/{id}/refresh")
    public LevEtfMarketDataService.RefreshResult refreshBenchmark(@PathVariable Long id,
                                                                  @RequestParam(defaultValue = "5y") String range) {
        return marketDataService.refreshBenchmarkHistory(uid(), id, range);
    }

    @PostMapping("/market-data/etf/{id}/refresh")
    public LevEtfMarketDataService.RefreshResult refreshEtf(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "5y") String range) {
        return marketDataService.refreshEtfHistory(uid(), id, range);
    }

    /** Manually enter/override a single close (for indices the provider can't quote). */
    @PostMapping("/market-data/{type}/{instrumentId}/manual-bar")
    public MarketDataBar manualBar(@PathVariable InstrumentType type, @PathVariable Long instrumentId,
                                   @RequestBody ManualBarRequest req) {
        return marketDataService.upsertManualBar(uid(), type, instrumentId, req.date(), req.close(), req.currency());
    }

    public record ManualBarRequest(LocalDate date, BigDecimal close, String currency) {}

    // ─────────────────────────── positions ───────────────────────────

    @GetMapping("/strategies/{id}/positions")
    public List<LevEtfPositionSnapshot> positions(@PathVariable Long id) {
        plannerService.requireStrategy(uid(), id);
        return positionRepository.findByUserIdAndStrategyId(uid(), id);
    }

    @PostMapping("/strategies/{id}/positions")
    public ResponseEntity<LevEtfPositionSnapshot> addPosition(@PathVariable Long id,
                                                              @RequestBody LevEtfPositionSnapshot p) {
        plannerService.requireStrategy(uid(), id);
        p.setUserId(uid());
        p.setId(null);
        p.setStrategyId(id);
        // Derive base value if not supplied, converting the original trading amount (never overwritten).
        if (p.getBaseCurrencyValue() == null && p.getMarketValue() != null) {
            p.setBaseCurrencyValue(fx.toBase(p.getMarketValue(), p.getTradingCurrency(), uid()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(positionRepository.save(p));
    }

    @DeleteMapping("/positions/{id}")
    public ResponseEntity<Void> deletePosition(@PathVariable Long id) {
        LevEtfPositionSnapshot e = positionRepository.findById(id).orElse(null);
        ownedOrThrow(e, e == null ? null : e.getUserId(), "Position");
        positionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── calculate / snapshots ───────────────────────────

    /** Server-authoritative recalculation for a strategy. Persists an immutable allocation snapshot. */
    @PostMapping("/strategies/{id}/calculate")
    public AllocationSnapshot calculate(@PathVariable Long id) {
        AllocationSnapshot snap = plannerService.computeAndSnapshot(uid(), id);
        // Evaluate alert preferences against the fresh snapshot (in-app + optional email). Best-effort.
        alertService.evaluateSnapshot(uid(), snap, alertPrefRepository.findByUserId(uid()));
        return snap;
    }

    @GetMapping("/strategies/{id}/snapshots")
    public List<AllocationSnapshot> snapshots(@PathVariable Long id) {
        plannerService.requireStrategy(uid(), id);
        return allocationSnapshotRepository.findByUserIdAndStrategyIdOrderByCalculationTimestampDesc(uid(), id);
    }

    @GetMapping("/strategies/{id}/snapshots/latest")
    public ResponseEntity<AllocationSnapshot> latestSnapshot(@PathVariable Long id) {
        plannerService.requireStrategy(uid(), id);
        return allocationSnapshotRepository
                .findFirstByUserIdAndStrategyIdOrderByCalculationTimestampDesc(uid(), id)
                .map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /** Stateless allocation-curve preview for the wizard: drawdown → target allocation, no persistence. */
    @PostMapping("/preview/allocation-curve")
    public List<Map<String, Object>> previewCurve(@RequestBody LevEtfStrategy s) {
        AllocationCalculationService.AllocationParams params = plannerService.toParams(s);
        java.util.ArrayList<Map<String, Object>> curve = new java.util.ArrayList<>();
        for (int dd = 0; dd <= 100; dd += 5) {
            BigDecimal target = engine.targetAllocationPercent(new BigDecimal(dd), params);
            Map<String, Object> row = new HashMap<>();
            row.put("drawdownPercent", dd);
            row.put("targetAllocationPercent", target);
            curve.add(row);
        }
        return curve;
    }

    // ─────────────────────────── rebalance plans ───────────────────────────

    @GetMapping("/rebalance-plans")
    public List<RebalancePlan> plans(@RequestParam(required = false) Long strategyId) {
        return rebalanceService.list(uid(), strategyId);
    }

    @PostMapping("/strategies/{id}/rebalance-plan")
    public RebalancePlan generatePlan(@PathVariable Long id) {
        return rebalanceService.generate(uid(), id);
    }

    @PostMapping("/rebalance-plans/{id}/approve")
    public RebalancePlan approvePlan(@PathVariable Long id) { return rebalanceService.approve(uid(), id); }

    @PostMapping("/rebalance-plans/{id}/reject")
    public RebalancePlan rejectPlan(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return rebalanceService.reject(uid(), id, body == null ? null : body.get("notes"));
    }

    @PostMapping("/rebalance-plans/{id}/execute")
    public RebalancePlan executePlan(@PathVariable Long id, @RequestBody ExecuteRequest req) {
        return rebalanceService.recordExecution(uid(), id, req.executedQuantity(), req.executedPrice(),
                req.fees(), req.brokerReference(), Boolean.TRUE.equals(req.partial()), req.notes());
    }

    public record ExecuteRequest(BigDecimal executedQuantity, BigDecimal executedPrice, BigDecimal fees,
                                 String brokerReference, Boolean partial, String notes) {}

    @DeleteMapping("/rebalance-plans/{id}")
    public ResponseEntity<Void> cancelPlan(@PathVariable Long id) {
        rebalanceService.cancel(uid(), id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── alert prefs ───────────────────────────

    @GetMapping("/alert-prefs")
    public List<LevEtfAlertPref> alertPrefs() { return alertPrefRepository.findByUserId(uid()); }

    @PostMapping("/alert-prefs")
    public ResponseEntity<LevEtfAlertPref> createAlertPref(@RequestBody LevEtfAlertPref p) {
        p.setUserId(uid());
        p.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(alertPrefRepository.save(p));
    }

    @DeleteMapping("/alert-prefs/{id}")
    public ResponseEntity<Void> deleteAlertPref(@PathVariable Long id) {
        LevEtfAlertPref e = alertPrefRepository.findById(id).orElse(null);
        ownedOrThrow(e, e == null ? null : e.getUserId(), "Alert preference");
        alertPrefRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── backtests ───────────────────────────

    @GetMapping("/backtests")
    public List<LevEtfBacktest> backtests() { return backtestService.list(uid()); }

    @GetMapping("/backtests/{id}")
    public LevEtfBacktest backtest(@PathVariable Long id) { return backtestService.get(uid(), id); }

    @PostMapping("/backtests")
    public LevEtfBacktest runBacktest(@RequestBody com.myfinance.service.levetf.LevEtfBacktestService.BacktestRequest req) {
        return backtestService.run(uid(), req);
    }

    // ─────────────────────────── notifications (in-app alert history) ───────────────────────────

    @GetMapping("/notifications")
    public List<LevEtfAlertHistory> notifications() { return alertService.list(uid()); }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", alertService.unreadCount(uid()));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        alertService.markRead(uid(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead() {
        alertService.markAllRead(uid());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private boolean changed(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
    }

    private boolean safeEq(String a, String b) {
        return java.util.Objects.equals(a == null ? "" : a, b == null ? "" : b);
    }
}
