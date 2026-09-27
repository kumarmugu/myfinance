package com.myfinance.service.levetf;

import com.myfinance.model.enums.levetf.AllocationMode;
import com.myfinance.model.enums.levetf.DataQuality;
import com.myfinance.model.enums.levetf.RebalanceAction;
import com.myfinance.model.enums.levetf.ReferenceHighMode;
import com.myfinance.service.levetf.AllocationCalculationService.AllocationParams;
import com.myfinance.service.levetf.AllocationCalculationService.AllocationResult;
import com.myfinance.service.levetf.AllocationCalculationService.HistoricalClose;
import com.myfinance.service.levetf.AllocationCalculationService.LadderRow;
import com.myfinance.service.levetf.AllocationCalculationService.PricePoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the allocation calculation engine — no Spring context, no DB. These are the
 * acceptance tests for the financial core; the logic is NOT mocked.
 */
class AllocationCalculationServiceTest {

    private final AllocationCalculationService svc = new AllocationCalculationService();

    /** Default example strategy: initial 10, multiplier 0.5, min 10, cap 50 (enabled). */
    private AllocationParams defaultParams() {
        return new AllocationParams(
                AllocationMode.INITIAL_PLUS_HALF_DRAWDOWN,
                new BigDecimal("10"), new BigDecimal("0.5"),
                new BigDecimal("10"), new BigDecimal("50"), true, null);
    }

    private PricePoint ok(String v) { return new PricePoint(new BigDecimal(v), DataQuality.OK); }

    private BigDecimal targetForDrawdown(String drawdown, AllocationParams p) {
        return svc.targetAllocationPercent(new BigDecimal(drawdown), p);
    }

    // ─────────── the mandated drawdown → allocation table ───────────

    @Test
    void mandatedAllocationTable_defaultStrategy() {
        AllocationParams p = defaultParams();
        assertEquals(0, new BigDecimal("10").compareTo(targetForDrawdown("0", p)), "0% -> 10%");
        assertEquals(0, new BigDecimal("10").compareTo(targetForDrawdown("20", p)), "20% -> 10%");
        assertEquals(0, new BigDecimal("15").compareTo(targetForDrawdown("30", p)), "30% -> 15%");
        assertEquals(0, new BigDecimal("20").compareTo(targetForDrawdown("40", p)), "40% -> 20%");
        assertEquals(0, new BigDecimal("25").compareTo(targetForDrawdown("50", p)), "50% -> 25%");
        assertEquals(0, new BigDecimal("30").compareTo(targetForDrawdown("60", p)), "60% -> 30%");
        assertEquals(0, new BigDecimal("40").compareTo(targetForDrawdown("80", p)), "80% -> 40%");
    }

    @Test
    void capAppliedWhenEnabled() {
        AllocationParams p = defaultParams(); // cap 50 enabled
        // 120% drawdown x 0.5 = 60 raw -> capped to 50.
        assertEquals(0, new BigDecimal("50").compareTo(targetForDrawdown("120", p)), "capped at 50");
    }

    @Test
    void capIgnoredWhenDisabled() {
        AllocationParams p = new AllocationParams(
                AllocationMode.INITIAL_PLUS_HALF_DRAWDOWN,
                new BigDecimal("10"), new BigDecimal("0.5"),
                new BigDecimal("10"), new BigDecimal("50"), false, null); // cap disabled
        // 120% x 0.5 = 60, no cap.
        assertEquals(0, new BigDecimal("60").compareTo(targetForDrawdown("120", p)), "no cap -> 60");
    }

    @Test
    void drawdownOnlyWithMinMode() {
        AllocationParams p = new AllocationParams(
                AllocationMode.DRAWDOWN_ONLY_WITH_MIN,
                new BigDecimal("10"), new BigDecimal("0.5"),
                new BigDecimal("10"), new BigDecimal("50"), true, null);
        assertEquals(0, new BigDecimal("10").compareTo(targetForDrawdown("10", p)), "5 raw < min 10 -> 10");
        assertEquals(0, new BigDecimal("20").compareTo(targetForDrawdown("40", p)), "40x0.5 = 20");
    }

    @Test
    void ladderModeReturnsCorrectRow() {
        List<LadderRow> ladder = List.of(
                new LadderRow(new BigDecimal("0"), new BigDecimal("10")),
                new LadderRow(new BigDecimal("25"), new BigDecimal("20")),
                new LadderRow(new BigDecimal("50"), new BigDecimal("35")));
        AllocationParams p = new AllocationParams(
                AllocationMode.LADDER, new BigDecimal("10"), new BigDecimal("0.5"),
                new BigDecimal("10"), new BigDecimal("50"), true, ladder);
        assertEquals(0, new BigDecimal("10").compareTo(targetForDrawdown("10", p)), "10% -> row 0 (10)");
        assertEquals(0, new BigDecimal("20").compareTo(targetForDrawdown("30", p)), "30% -> row 25 (20)");
        assertEquals(0, new BigDecimal("35").compareTo(targetForDrawdown("70", p)), "70% -> row 50 (35)");
    }

    // ─────────── drawdown from prices ───────────

    @Test
    void calculateDrawdownFromPrices() {
        // ref 100, current 60 -> 40% drawdown -> target 20 (default).
        AllocationResult r = svc.calculate(ok("100"), ok("60"), defaultParams());
        assertFalse(r.blocked());
        assertEquals(0, new BigDecimal("40").compareTo(r.drawdownPercent()));
        assertEquals(0, new BigDecimal("20").compareTo(r.targetAllocationPercent()));
        assertEquals(AllocationMode.INITIAL_PLUS_HALF_DRAWDOWN, r.mode());
    }

    @Test
    void indexAboveReferenceHighGivesZeroDrawdownAndFloor() {
        // current 120 > ref 100 -> drawdown clamped to 0 -> target = initial 10.
        AllocationResult r = svc.calculate(ok("100"), ok("120"), defaultParams());
        assertFalse(r.blocked());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.drawdownPercent()));
        assertEquals(0, new BigDecimal("10").compareTo(r.targetAllocationPercent()));
    }

    // ─────────── invalid / missing / stale inputs ───────────

    @Test
    void missingReferenceHighBlocks() {
        AllocationResult r = svc.calculate(new PricePoint(null, DataQuality.MISSING), ok("60"), defaultParams());
        assertTrue(r.blocked());
        assertNull(r.targetAllocationPercent());
        assertTrue(r.blockedReason().toLowerCase().contains("reference high"));
    }

    @Test
    void zeroOrNegativeBenchmarkBlocks() {
        assertTrue(svc.calculate(ok("100"), new PricePoint(BigDecimal.ZERO, DataQuality.OK), defaultParams()).blocked());
        assertTrue(svc.calculate(ok("100"), new PricePoint(new BigDecimal("-5"), DataQuality.OK), defaultParams()).blocked());
        assertTrue(svc.calculate(new PricePoint(BigDecimal.ZERO, DataQuality.OK), ok("60"), defaultParams()).blocked());
    }

    @Test
    void stalePriceBlocksAndNeverSubstitutesZero() {
        AllocationResult r = svc.calculate(ok("100"), new PricePoint(new BigDecimal("60"), DataQuality.STALE), defaultParams());
        assertTrue(r.blocked());
        assertNull(r.drawdownPercent(), "no drawdown computed on stale data");
        assertTrue(r.blockedReason().contains("STALE"));
    }

    // ─────────── reference high modes ───────────

    private HistoricalClose close(String date, String v) {
        return new HistoricalClose(LocalDate.parse(date), new BigDecimal(v), DataQuality.OK);
    }

    @Test
    void allTimeReferenceHighIsMaxValidClose() {
        List<HistoricalClose> h = List.of(close("2024-01-01", "100"), close("2024-02-01", "150"),
                close("2024-03-01", "120"),
                new HistoricalClose(LocalDate.parse("2024-04-01"), new BigDecimal("999"), DataQuality.STALE)); // ignored
        BigDecimal ref = svc.referenceHigh(ReferenceHighMode.ALL_TIME, h, null, 252);
        assertEquals(0, new BigDecimal("150").compareTo(ref), "stale 999 excluded; max valid = 150");
    }

    @Test
    void customStartDateReferenceHighExcludesEarlier() {
        List<HistoricalClose> h = List.of(close("2024-01-01", "200"), close("2024-06-01", "120"),
                close("2024-07-01", "140"));
        BigDecimal ref = svc.referenceHigh(ReferenceHighMode.CUSTOM_START_DATE, h, LocalDate.parse("2024-05-01"), 252);
        assertEquals(0, new BigDecimal("140").compareTo(ref), "200 before start excluded; max after = 140");
    }

    @Test
    void rolling52WeekUsesLastNSessions() {
        // 5 sessions, window of 3 -> last 3 = 130,120,110 max 130 (older 200 excluded).
        List<HistoricalClose> h = List.of(close("2024-01-01", "200"), close("2024-01-02", "150"),
                close("2024-01-03", "130"), close("2024-01-04", "120"), close("2024-01-05", "110"));
        BigDecimal ref = svc.referenceHigh(ReferenceHighMode.ROLLING_52_WEEK, h, null, 3);
        assertEquals(0, new BigDecimal("130").compareTo(ref));
    }

    @Test
    void manualReferenceHighNotComputedFromHistory() {
        assertNull(svc.referenceHigh(ReferenceHighMode.MANUAL, List.of(close("2024-01-01", "100")), null, 252));
    }

    @Test
    void referenceHighNullWhenNoValidCloses() {
        List<HistoricalClose> h = List.of(new HistoricalClose(LocalDate.parse("2024-01-01"), new BigDecimal("100"), DataQuality.MISSING));
        assertNull(svc.referenceHigh(ReferenceHighMode.ALL_TIME, h, null, 252));
    }

    // ─────────── rebalance decisions ───────────

    private AllocationCalculationService.RebalanceResult rebal(String portfolio, String currentEtf, String target,
                                                               String tol, String etfPrice, String fx) {
        return svc.rebalance(new BigDecimal(portfolio), DataQuality.OK,
                new BigDecimal(currentEtf), new BigDecimal(target), new BigDecimal(tol),
                new BigDecimal(etfPrice), DataQuality.OK, new BigDecimal(fx),
                4, true, null);
    }

    @Test
    void rebalanceBuyWhenUnderTarget() {
        // portfolio 100000 base, target 20% = 20000; current 5000 -> BUY 15000.
        var r = rebal("100000", "5000", "20", "1", "100", "1");
        assertFalse(r.blocked());
        assertEquals(RebalanceAction.BUY, r.action());
        assertEquals(0, new BigDecimal("20000.00").compareTo(r.targetEtfValueBase()));
        assertEquals(0, new BigDecimal("15000.00").compareTo(r.rebalanceDifferenceBase()));
        assertTrue(r.estimatedQuantity().signum() > 0);
    }

    @Test
    void rebalanceReduceWhenOverTarget() {
        // target 10% = 10000; current 30000 -> REDUCE (negative difference).
        var r = rebal("100000", "30000", "10", "1", "100", "1");
        assertEquals(RebalanceAction.REDUCE, r.action());
        assertEquals(0, new BigDecimal("-20000.00").compareTo(r.rebalanceDifferenceBase()));
    }

    @Test
    void rebalanceNoActionWithinTolerance() {
        // target 20%, current 20500/100000 = 20.5% -> gap 0.5 <= tol 1 -> NO_ACTION.
        var r = rebal("100000", "20500", "20", "1", "100", "1");
        assertEquals(RebalanceAction.NO_ACTION, r.action());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.estimatedQuantity()));
    }

    @Test
    void rebalanceBlockedOnZeroPortfolio() {
        var r = svc.rebalance(BigDecimal.ZERO, DataQuality.OK, new BigDecimal("100"),
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("100"), DataQuality.OK,
                new BigDecimal("1"), 4, true, null);
        assertTrue(r.blocked());
    }

    @Test
    void rebalanceBlockedOnNegativePortfolio() {
        var r = svc.rebalance(new BigDecimal("-1000"), DataQuality.OK, new BigDecimal("100"),
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("100"), DataQuality.OK,
                new BigDecimal("1"), 4, true, null);
        assertTrue(r.blocked());
    }

    @Test
    void rebalanceBlockedOnStalePortfolio() {
        var r = svc.rebalance(new BigDecimal("100000"), DataQuality.STALE, new BigDecimal("100"),
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("100"), DataQuality.OK,
                new BigDecimal("1"), 4, true, null);
        assertTrue(r.blocked());
        assertTrue(r.blockedReason().contains("STALE"));
    }

    @Test
    void rebalanceBlockedOnMissingFx() {
        var r = svc.rebalance(new BigDecimal("100000"), DataQuality.OK, new BigDecimal("100"),
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("100"), DataQuality.OK,
                null, 4, true, null);
        assertTrue(r.blocked());
        assertTrue(r.blockedReason().toLowerCase().contains("fx"));
    }

    @Test
    void rebalanceBlockedOnStaleEtfPrice() {
        var r = svc.rebalance(new BigDecimal("100000"), DataQuality.OK, new BigDecimal("100"),
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("100"), DataQuality.STALE,
                new BigDecimal("1"), 4, true, null);
        assertTrue(r.blocked());
    }

    @Test
    void fractionalDisabledRoundsDownToWholeShares() {
        // BUY 15000 base at price 99, fx 1 -> 151.515 shares -> floored to 151 when fractional off.
        var r = svc.rebalance(new BigDecimal("100000"), DataQuality.OK, new BigDecimal("5000"),
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("99"), DataQuality.OK,
                new BigDecimal("1"), 4, false, null);
        assertEquals(RebalanceAction.BUY, r.action());
        assertEquals(0, new BigDecimal("151").compareTo(r.estimatedQuantity()), "floored to 151 whole shares");
        assertTrue(r.residualBase().signum() >= 0, "residual is the uncovered base amount");
    }

    @Test
    void lotSizeSnapsDownToWholeLots() {
        // current 5500 -> diff 14500 at price 10 -> 1450 shares -> lot 100 -> 1400.
        var r = svc.rebalance(new BigDecimal("100000"), DataQuality.OK, new BigDecimal("5500"),
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("10"), DataQuality.OK,
                new BigDecimal("1"), 4, false, new BigDecimal("100"));
        assertEquals(0, new BigDecimal("1400").compareTo(r.estimatedQuantity()), "1450 snapped down to 1400 (lot 100)");
    }

    @Test
    void crossCurrencyEtfUsesFxForQuantity() {
        // portfolio base 100000, target 20% = 20000 base; current 0 -> diff 20000 base.
        // ETF priced 50 (USD), fx USD->base = 1.35 -> diff in trading = 20000/1.35 = 14814.81 -> /50 = 296.29.
        var r = svc.rebalance(new BigDecimal("100000"), DataQuality.OK, BigDecimal.ZERO,
                new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("50"), DataQuality.OK,
                new BigDecimal("1.35"), 4, true, null);
        assertEquals(RebalanceAction.BUY, r.action());
        assertTrue(r.estimatedQuantity().compareTo(new BigDecimal("296.29")) >= 0
                && r.estimatedQuantity().compareTo(new BigDecimal("296.30")) <= 0,
                "quantity ~296.29 using FX, got " + r.estimatedQuantity());
    }
}
