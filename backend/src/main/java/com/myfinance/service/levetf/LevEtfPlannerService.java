package com.myfinance.service.levetf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.*;
import com.myfinance.model.enums.levetf.*;
import com.myfinance.repository.*;
import com.myfinance.service.CurrencyConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates a strategy's allocation calculation end-to-end: resolves the benchmark reference high
 * and current index from stored {@link MarketDataBar}s, runs the pure {@link AllocationCalculationService},
 * resolves the portfolio scope value and the ETF's current value (FX-converted to base via
 * {@link CurrencyConversionService}), computes the rebalance, and persists an immutable
 * {@link AllocationSnapshot}. It never mutates original per-record currency/amounts, never substitutes
 * zero for missing/stale/invalid data (it blocks with a reason), and stamps the strategy's
 * {@code ruleVersion} so later rule edits cannot change history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevEtfPlannerService {

    private final LevEtfStrategyRepository strategyRepository;
    private final BenchmarkIndexRepository benchmarkRepository;
    private final LevEtfInstrumentRepository instrumentRepository;
    private final LevEtfPositionSnapshotRepository positionRepository;
    private final AllocationSnapshotRepository allocationSnapshotRepository;
    private final LevEtfMarketDataService marketDataService;
    private final AllocationCalculationService engine;
    private final CurrencyConversionService fx;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Owns-or-throw helper for a strategy. */
    public LevEtfStrategy requireStrategy(Long userId, Long strategyId) {
        return strategyRepository.findById(strategyId)
                .filter(s -> s.getUserId() != null && s.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Strategy not found"));
    }

    /**
     * Compute (and persist) an allocation snapshot for the strategy using the latest stored market data
     * and the user's current portfolio positions. Blocks (persists a blocked snapshot) when inputs are
     * missing/stale/invalid rather than fabricating a number.
     */
    @Transactional
    public AllocationSnapshot computeAndSnapshot(Long userId, Long strategyId) {
        LevEtfStrategy strategy = requireStrategy(userId, strategyId);

        AllocationSnapshot.AllocationSnapshotBuilder snap = AllocationSnapshot.builder()
                .userId(userId)
                .strategyId(strategyId)
                .ruleVersion(strategy.getRuleVersion())
                .calculationTimestamp(LocalDateTime.now());

        // ── 1. Benchmark reference high + current index ──
        if (strategy.getBenchmarkIndexId() == null) {
            return allocationSnapshotRepository.save(snap.dataQuality(DataQuality.MISSING)
                    .blockedReason("No benchmark configured for this strategy.").build());
        }
        Optional<MarketDataBar> latestIdx = marketDataService.latestBar(
                userId, InstrumentType.BENCHMARK, strategy.getBenchmarkIndexId());
        if (latestIdx.isEmpty()) {
            return allocationSnapshotRepository.save(snap.dataQuality(DataQuality.MISSING)
                    .blockedReason("No benchmark price history — refresh or enter it, then recalculate.").build());
        }
        MarketDataBar idxBar = latestIdx.get();
        snap.benchmarkPrice(idxBar.getClose())
                .benchmarkPriceTimestamp(idxBar.getSourceTimestamp());

        BigDecimal referenceHigh = resolveReferenceHigh(userId, strategy);
        if (referenceHigh == null || referenceHigh.signum() <= 0) {
            return allocationSnapshotRepository.save(snap.dataQuality(DataQuality.MISSING)
                    .blockedReason("Reference high could not be determined from the available data.").build());
        }
        snap.referenceHigh(referenceHigh).referenceHighDate(strategy.getReferenceHighDate());

        // ── 2. Drawdown → target allocation (pure engine) ──
        AllocationCalculationService.AllocationParams params = toParams(strategy);
        AllocationCalculationService.PricePoint refHighPp =
                new AllocationCalculationService.PricePoint(referenceHigh, DataQuality.OK);
        AllocationCalculationService.PricePoint curPp =
                new AllocationCalculationService.PricePoint(idxBar.getClose(), idxBar.getDataQuality());
        AllocationCalculationService.AllocationResult alloc = engine.calculate(refHighPp, curPp, params);
        if (alloc.blocked()) {
            return allocationSnapshotRepository.save(snap.dataQuality(idxBar.getDataQuality())
                    .blockedReason(alloc.blockedReason()).build());
        }
        snap.drawdownPercent(alloc.drawdownPercent()).targetAllocationPercent(alloc.targetAllocationPercent());

        // ── 3. Portfolio scope value (base currency) ──
        BigDecimal portfolioValueBase = resolvePortfolioValueBase(userId, strategy);
        snap.portfolioValueBase(portfolioValueBase);

        // ── 4. Current ETF value + rebalance ──
        BigDecimal currentEtfValueBase = currentEtfValueBase(userId, strategy);
        snap.currentEtfValueBase(currentEtfValueBase);

        LevEtfInstrument etf = strategy.getEtfInstrumentId() == null ? null
                : instrumentRepository.findById(strategy.getEtfInstrumentId())
                    .filter(e -> e.getUserId() != null && e.getUserId().equals(userId)).orElse(null);

        BigDecimal etfPriceTrading = null;
        DataQuality etfQuality = DataQuality.MISSING;
        if (etf != null) {
            Optional<MarketDataBar> latestEtf = marketDataService.latestBar(userId, InstrumentType.ETF, etf.getId());
            if (latestEtf.isPresent()) {
                etfPriceTrading = latestEtf.get().getClose();
                etfQuality = latestEtf.get().getDataQuality();
                snap.etfPriceTimestamp(latestEtf.get().getSourceTimestamp());
            }
        }
        String tradingCurrency = etf != null && etf.getTradingCurrency() != null
                ? etf.getTradingCurrency() : strategy.getTradingCurrency();
        BigDecimal fxRate = tradingCurrency == null ? BigDecimal.ONE : fx.factorToBase(tradingCurrency, userId);
        snap.fxRate(fxRate);

        boolean fractionalAllowed = true; // fractional shares permitted by default; instrument metadata may refine later
        AllocationCalculationService.RebalanceResult rb = engine.rebalance(
                portfolioValueBase, DataQuality.OK,
                currentEtfValueBase,
                alloc.targetAllocationPercent(),
                strategy.getRebalanceTolerancePercent(),
                etfPriceTrading, etfQuality,
                fxRate,
                4, fractionalAllowed, BigDecimal.ONE);

        if (!rb.blocked()) {
            snap.actualAllocationPercent(rb.actualAllocationPercent())
                .targetEtfValueBase(rb.targetEtfValueBase())
                .rebalanceDifferenceBase(rb.rebalanceDifferenceBase())
                .dataQuality(DataQuality.OK);
        } else {
            // Allocation succeeded but rebalance couldn't (e.g. no ETF price) — keep the allocation
            // figures and record why the rebalance is unavailable, without fabricating a proposal.
            snap.dataQuality(etfQuality).blockedReason(rb.blockedReason());
        }
        return allocationSnapshotRepository.save(snap.build());
    }

    /** Resolve the strategy's reference high: MANUAL uses the stored value, others derive from history. */
    BigDecimal resolveReferenceHigh(Long userId, LevEtfStrategy strategy) {
        ReferenceHighMode mode = strategy.getReferenceHighMode() == null
                ? ReferenceHighMode.ALL_TIME : strategy.getReferenceHighMode();
        if (mode == ReferenceHighMode.MANUAL) return strategy.getReferenceHighValue();
        if (Boolean.TRUE.equals(strategy.getReferenceHighFrozen()) && strategy.getReferenceHighValue() != null) {
            return strategy.getReferenceHighValue();
        }
        List<MarketDataBar> bars = marketDataService.history(userId, InstrumentType.BENCHMARK, strategy.getBenchmarkIndexId());
        List<AllocationCalculationService.HistoricalClose> closes = marketDataService.toHistoricalCloses(bars);
        return engine.referenceHigh(mode, closes, strategy.getReferenceHighDate(), 252);
    }

    /** Portfolio value in base currency per the strategy's scope. */
    BigDecimal resolvePortfolioValueBase(Long userId, LevEtfStrategy strategy) {
        PortfolioScopeType scope = strategy.getPortfolioScope() == null
                ? PortfolioScopeType.WHOLE : strategy.getPortfolioScope();
        return switch (scope) {
            case MANUAL -> strategy.getScopeManualValue() == null ? BigDecimal.ZERO : strategy.getScopeManualValue();
            // ACCOUNT / WHOLE: use the sum of the user's recorded lev-ETF positions for the strategy as the
            // conservative, self-contained scope value. (Whole-portfolio net worth wiring can refine later.)
            case ACCOUNT, WHOLE -> currentEtfValueBase(userId, strategy).max(
                    strategy.getScopeManualValue() == null ? BigDecimal.ZERO : strategy.getScopeManualValue());
        };
    }

    /** Current ETF market value in base currency, summed from the strategy's recorded positions. */
    BigDecimal currentEtfValueBase(Long userId, LevEtfStrategy strategy) {
        List<LevEtfPositionSnapshot> positions = positionRepository.findByUserIdAndStrategyId(userId, strategy.getId());
        BigDecimal total = BigDecimal.ZERO;
        for (LevEtfPositionSnapshot p : positions) {
            if (p.getBaseCurrencyValue() != null) {
                total = total.add(p.getBaseCurrencyValue());
            } else if (p.getMarketValue() != null) {
                total = total.add(fx.toBase(p.getMarketValue(), p.getTradingCurrency(), userId));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Build engine params from the strategy, parsing the ladder JSON when in LADDER mode. */
    public AllocationCalculationService.AllocationParams toParams(LevEtfStrategy s) {
        List<AllocationCalculationService.LadderRow> ladder = parseLadder(s.getLadderJson());
        return new AllocationCalculationService.AllocationParams(
                s.getAllocationMode() == null ? AllocationMode.INITIAL_PLUS_HALF_DRAWDOWN : s.getAllocationMode(),
                s.getInitialAllocationPercent(),
                s.getDrawdownMultiplier(),
                s.getMinimumAllocationPercent(),
                s.getMaximumAllocationPercent(),
                Boolean.TRUE.equals(s.getMaximumAllocationEnabled()),
                ladder);
    }

    List<AllocationCalculationService.LadderRow> parseLadder(String json) {
        List<AllocationCalculationService.LadderRow> rows = new ArrayList<>();
        if (json == null || json.isBlank()) return rows;
        try {
            JsonNode arr = mapper.readTree(json);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    JsonNode t = n.has("threshold") ? n.get("threshold") : n.get("drawdownThresholdPercent");
                    JsonNode a = n.has("allocation") ? n.get("allocation") : n.get("allocationPercent");
                    if (t != null && a != null && t.isNumber() && a.isNumber()) {
                        rows.add(new AllocationCalculationService.LadderRow(t.decimalValue(), a.decimalValue()));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Ladder JSON parse failed: {}", e.toString());
        }
        return rows;
    }
}
