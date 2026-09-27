package com.myfinance.service.levetf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.*;
import com.myfinance.model.enums.levetf.InstrumentType;
import com.myfinance.repository.LevEtfBacktestRepository;
import com.myfinance.repository.MarketDataBarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a strategy backtest strictly chronologically with NO look-ahead: on each rebalance date the
 * reference high is computed only from bars up to and including that date, and the ETF's OWN historical
 * price series drives P/L (never a fabricated multiple-of-index). Path dependency is preserved by
 * carrying cash + ETF units forward bar by bar. Results and explicit caveats are persisted as JSON.
 *
 * <p>This is a historical simulation, NOT a prediction. Fees/slippage/FX are applied only when the
 * caller configures them; when omitted, the report labels the result as gross of those costs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevEtfBacktestService {

    private final MarketDataBarRepository barRepository;
    private final LevEtfBacktestRepository backtestRepository;
    private final LevEtfPlannerService plannerService;
    private final AllocationCalculationService engine;

    private final ObjectMapper mapper = new ObjectMapper();

    public record BacktestRequest(Long strategyId, Long benchmarkIndexId, Long etfInstrumentId,
                                  BigDecimal initialPortfolioValue, LocalDate startDate, LocalDate endDate,
                                  String rebalanceFrequency, BigDecimal feePercent, BigDecimal slippagePercent) {}

    public List<LevEtfBacktest> list(Long userId) {
        return backtestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public LevEtfBacktest get(Long userId, Long id) {
        return backtestRepository.findById(id)
                .filter(b -> b.getUserId() != null && b.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Backtest not found"));
    }

    /** Run and persist a backtest for the strategy over the stored series. */
    @Transactional
    public LevEtfBacktest run(Long userId, BacktestRequest req) {
        LevEtfStrategy strategy = plannerService.requireStrategy(userId, req.strategyId());
        Long benchmarkId = req.benchmarkIndexId() != null ? req.benchmarkIndexId() : strategy.getBenchmarkIndexId();
        Long etfId = req.etfInstrumentId() != null ? req.etfInstrumentId() : strategy.getEtfInstrumentId();

        LevEtfBacktest bt = LevEtfBacktest.builder()
                .userId(userId).strategyId(req.strategyId()).ruleVersion(strategy.getRuleVersion())
                .benchmarkIndexId(benchmarkId).etfInstrumentId(etfId)
                .initialPortfolioValue(req.initialPortfolioValue())
                .startDate(req.startDate()).endDate(req.endDate())
                .rebalanceFrequency(req.rebalanceFrequency())
                .status("RUNNING")
                .build();

        List<String> warnings = new ArrayList<>();
        if (benchmarkId == null || etfId == null) {
            return fail(bt, "Backtest needs both a benchmark and an ETF instrument.");
        }
        List<MarketDataBar> idxBars = inRange(barRepository
                .findByUserIdAndInstrumentTypeAndInstrumentIdOrderByDateAsc(userId, InstrumentType.BENCHMARK, benchmarkId),
                req.startDate(), req.endDate());
        List<MarketDataBar> etfBars = inRange(barRepository
                .findByUserIdAndInstrumentTypeAndInstrumentIdOrderByDateAsc(userId, InstrumentType.ETF, etfId),
                req.startDate(), req.endDate());
        if (idxBars.isEmpty() || etfBars.isEmpty()) {
            return fail(bt, "Not enough stored history for the benchmark and/or ETF in the selected range. "
                    + "Refresh market data first.");
        }

        // Index the ETF closes by date for O(1) lookup; iterate benchmark dates chronologically.
        Map<LocalDate, BigDecimal> etfClose = new LinkedHashMap<>();
        for (MarketDataBar b : etfBars) if (b.getClose() != null) etfClose.put(b.getDate(), b.getClose());

        BigDecimal initial = req.initialPortfolioValue() == null ? new BigDecimal("100000") : req.initialPortfolioValue();
        BigDecimal cash = initial;
        BigDecimal units = BigDecimal.ZERO;
        BigDecimal fee = req.feePercent() == null ? BigDecimal.ZERO : req.feePercent();
        BigDecimal slip = req.slippagePercent() == null ? BigDecimal.ZERO : req.slippagePercent();
        if (fee.signum() == 0 && slip.signum() == 0) warnings.add("Gross of fees and slippage (none configured).");
        warnings.add("Historical simulation using the ETF's own price series — not a prediction. FX not modelled unless the bars share one currency.");

        AllocationCalculationService.AllocationParams params = plannerService.toParams(strategy);
        List<AllocationCalculationService.HistoricalClose> runningCloses = new ArrayList<>();
        List<Map<String, Object>> series = new ArrayList<>();
        int rebalanceEvery = "WEEKLY".equalsIgnoreCase(req.rebalanceFrequency()) ? 5
                : "MONTHLY".equalsIgnoreCase(req.rebalanceFrequency()) ? 21 : 1; // trading-day cadence
        int sinceRebalance = 0;
        BigDecimal peakEquity = initial;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (MarketDataBar idx : idxBars) {
            if (idx.getClose() == null || idx.getClose().signum() <= 0) continue;
            runningCloses.add(new AllocationCalculationService.HistoricalClose(
                    idx.getDate(), idx.getClose(),
                    idx.getDataQuality() == null ? com.myfinance.model.enums.levetf.DataQuality.OK : idx.getDataQuality()));

            BigDecimal etfPrice = etfClose.get(idx.getDate());
            if (etfPrice == null || etfPrice.signum() <= 0) continue; // no tradeable ETF price that day

            BigDecimal equity = cash.add(units.multiply(etfPrice));
            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
            if (peakEquity.signum() > 0) {
                BigDecimal dd = peakEquity.subtract(equity).divide(peakEquity, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                if (dd.compareTo(maxDrawdown) > 0) maxDrawdown = dd;
            }

            if (sinceRebalance % rebalanceEvery == 0) {
                // Reference high from history UP TO TODAY only (no look-ahead).
                BigDecimal refHigh = engine.referenceHigh(
                        strategy.getReferenceHighMode() == null
                                ? com.myfinance.model.enums.levetf.ReferenceHighMode.ALL_TIME : strategy.getReferenceHighMode(),
                        runningCloses, strategy.getReferenceHighDate(), 252);
                if (refHigh != null && refHigh.signum() > 0) {
                    AllocationCalculationService.AllocationResult a = engine.calculate(
                            new AllocationCalculationService.PricePoint(refHigh, com.myfinance.model.enums.levetf.DataQuality.OK),
                            new AllocationCalculationService.PricePoint(idx.getClose(), com.myfinance.model.enums.levetf.DataQuality.OK),
                            params);
                    if (!a.blocked()) {
                        BigDecimal targetValue = equity.multiply(a.targetAllocationPercent())
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                        BigDecimal currentValue = units.multiply(etfPrice);
                        BigDecimal diff = targetValue.subtract(currentValue);
                        // Trade the difference at the day's ETF close, applying slippage + fee on the traded notional.
                        BigDecimal execPrice = etfPrice.multiply(BigDecimal.ONE.add(
                                diff.signum() >= 0 ? slip.movePointLeft(2) : slip.movePointLeft(2).negate()));
                        if (execPrice.signum() > 0) {
                            BigDecimal tradeUnits = diff.divide(execPrice, 6, RoundingMode.DOWN);
                            BigDecimal notional = tradeUnits.abs().multiply(execPrice);
                            BigDecimal feeCost = notional.multiply(fee.movePointLeft(2));
                            units = units.add(tradeUnits);
                            cash = cash.subtract(tradeUnits.multiply(execPrice)).subtract(feeCost);
                        }
                    }
                }
            }
            sinceRebalance++;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", idx.getDate().toString());
            point.put("equity", equity.setScale(2, RoundingMode.HALF_UP));
            point.put("indexClose", idx.getClose());
            point.put("etfClose", etfPrice);
            series.add(point);
        }

        BigDecimal finalEquity = series.isEmpty() ? initial
                : (BigDecimal) series.get(series.size() - 1).get("equity");
        BigDecimal totalReturnPct = initial.signum() == 0 ? BigDecimal.ZERO
                : finalEquity.subtract(initial).divide(initial, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("initialValue", initial);
        results.put("finalValue", finalEquity);
        results.put("totalReturnPercent", totalReturnPct.setScale(2, RoundingMode.HALF_UP));
        results.put("maxDrawdownPercent", maxDrawdown.setScale(2, RoundingMode.HALF_UP));
        results.put("dataPoints", series.size());
        results.put("series", series);

        try {
            bt.setConfigJson(mapper.writeValueAsString(req));
            bt.setResultsJson(mapper.writeValueAsString(results));
        } catch (Exception e) {
            warnings.add("Result serialization issue: " + e.getMessage());
        }
        bt.setWarnings(String.join(" ", warnings));
        bt.setStatus("DONE");
        bt.setCompletedAt(LocalDateTime.now());
        LevEtfBacktest saved = backtestRepository.save(bt);
        log.info("Backtest done id={} userId={} points={} return={}%", saved.getId(), userId, series.size(),
                totalReturnPct.setScale(2, RoundingMode.HALF_UP));
        return saved;
    }

    private LevEtfBacktest fail(LevEtfBacktest bt, String reason) {
        bt.setStatus("FAILED");
        bt.setWarnings(reason);
        bt.setCompletedAt(LocalDateTime.now());
        return backtestRepository.save(bt);
    }

    private List<MarketDataBar> inRange(List<MarketDataBar> bars, LocalDate start, LocalDate end) {
        return bars.stream()
                .filter(b -> b.getDate() != null
                        && (start == null || !b.getDate().isBefore(start))
                        && (end == null || !b.getDate().isAfter(end)))
                .toList();
    }
}
