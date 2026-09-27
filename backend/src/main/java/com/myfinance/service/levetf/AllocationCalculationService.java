package com.myfinance.service.levetf;

import com.myfinance.model.enums.levetf.AllocationMode;
import com.myfinance.model.enums.levetf.DataQuality;
import com.myfinance.model.enums.levetf.RebalanceAction;
import com.myfinance.model.enums.levetf.ReferenceHighMode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Pure, independently-testable core of the Leveraged ETF Allocation Planner. It contains ONLY financial
 * math — no Spring web, no JPA, no HTTP, no persistence. Every method takes plain inputs and returns a
 * value; the orchestration services (which do load entities and persist snapshots) call into here.
 *
 * <p>Design rules enforced here:
 * <ul>
 *   <li>All money / price / quantity / rate / percent values are {@link BigDecimal}; no {@code double}.</li>
 *   <li>The <b>benchmark</b> price drives the drawdown — the leveraged ETF's own price is NEVER used for it.</li>
 *   <li>Missing / stale / invalid / nonpositive inputs are refused with a {@code blockedReason};
 *       a value is NEVER silently replaced with zero.</li>
 *   <li>Allocation modes are never mixed; the caller selects exactly one and it is echoed back.</li>
 * </ul>
 */
@Service
public class AllocationCalculationService {

    /** Scale for intermediate/percent math. */
    private static final int PCT_SCALE = 6;
    /** Scale for money results. */
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // ─────────────────────────── inputs ───────────────────────────

    /** One row of a user-defined allocation ladder: at/above {@code drawdownThresholdPercent}, use {@code allocationPercent}. */
    public record LadderRow(BigDecimal drawdownThresholdPercent, BigDecimal allocationPercent) {}

    /**
     * The allocation rule parameters for a strategy version. {@code ladder} is only used for
     * {@link AllocationMode#LADDER}. All percents are 0–100.
     */
    public record AllocationParams(
            AllocationMode mode,
            BigDecimal initialAllocationPercent,
            BigDecimal drawdownMultiplier,
            BigDecimal minimumAllocationPercent,
            BigDecimal maximumAllocationPercent,
            boolean maximumEnabled,
            List<LadderRow> ladder) {}

    /**
     * A benchmark price point with its quality, so the engine can refuse to calculate on anything
     * other than {@link DataQuality#OK} data.
     */
    public record PricePoint(BigDecimal value, DataQuality quality) {}

    // ─────────────────────────── results ───────────────────────────

    /** Result of the allocation computation. When {@code blocked}, {@code targetAllocationPercent} is null. */
    public record AllocationResult(
            boolean blocked,
            String blockedReason,
            BigDecimal drawdownPercent,
            BigDecimal rawAllocationPercent,
            BigDecimal targetAllocationPercent,
            AllocationMode mode) {

        static AllocationResult blocked(String reason) {
            return new AllocationResult(true, reason, null, null, null, null);
        }
    }

    /** Result of the rebalance computation. When {@code blocked}, no proposal should be generated. */
    public record RebalanceResult(
            boolean blocked,
            String blockedReason,
            RebalanceAction action,
            BigDecimal targetEtfValueBase,
            BigDecimal currentEtfValueBase,
            BigDecimal rebalanceDifferenceBase,
            BigDecimal actualAllocationPercent,
            BigDecimal estimatedQuantity,
            BigDecimal residualBase) {

        static RebalanceResult blocked(String reason) {
            return new RebalanceResult(true, reason, RebalanceAction.NO_ACTION,
                    null, null, null, null, null, null);
        }
    }

    // ─────────────────────────── drawdown ───────────────────────────

    /**
     * Drawdown percent = max(0, (referenceHigh − currentIndex) / referenceHigh × 100). Both inputs must
     * be {@link DataQuality#OK} and positive; otherwise the result is blocked (never zero-substituted).
     */
    public AllocationResult calculate(PricePoint referenceHigh, PricePoint currentIndex, AllocationParams params) {
        String block = validatePrice("reference high", referenceHigh);
        if (block == null) block = validatePrice("current index price", currentIndex);
        if (block != null) return AllocationResult.blocked(block);

        BigDecimal ref = referenceHigh.value();
        BigDecimal cur = currentIndex.value();

        BigDecimal drawdown = ref.subtract(cur)
                .divide(ref, PCT_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
        if (drawdown.signum() < 0) drawdown = BigDecimal.ZERO.setScale(PCT_SCALE); // index above the high → 0

        BigDecimal raw = drawdown.multiply(nz(params.drawdownMultiplier())).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal target = applyMode(drawdown, raw, params);

        return new AllocationResult(false, null,
                drawdown.stripTrailingZeros(), raw.stripTrailingZeros(),
                target.stripTrailingZeros(), params.mode());
    }

    /** Target allocation for a KNOWN drawdown (used by UI previews and the ladder editor). */
    public BigDecimal targetAllocationPercent(BigDecimal drawdownPercent, AllocationParams params) {
        BigDecimal drawdown = drawdownPercent == null ? BigDecimal.ZERO : drawdownPercent.max(BigDecimal.ZERO);
        BigDecimal raw = drawdown.multiply(nz(params.drawdownMultiplier())).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        return applyMode(drawdown, raw, params).stripTrailingZeros();
    }

    private BigDecimal applyMode(BigDecimal drawdown, BigDecimal raw, AllocationParams params) {
        BigDecimal target = switch (params.mode()) {
            case INITIAL_PLUS_HALF_DRAWDOWN -> raw.max(nz(params.initialAllocationPercent()));
            case DRAWDOWN_ONLY_WITH_MIN -> raw.max(nz(params.minimumAllocationPercent()));
            case LADDER -> ladderLookup(drawdown, params.ladder());
        };
        if (params.maximumEnabled() && params.maximumAllocationPercent() != null) {
            target = target.min(params.maximumAllocationPercent());
        }
        return target.setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** Highest ladder row whose threshold ≤ drawdown; 0 if none matches / ladder empty. */
    private BigDecimal ladderLookup(BigDecimal drawdown, List<LadderRow> ladder) {
        if (ladder == null || ladder.isEmpty()) return BigDecimal.ZERO;
        BigDecimal best = BigDecimal.ZERO;
        BigDecimal bestThreshold = null;
        for (LadderRow row : ladder) {
            if (row.drawdownThresholdPercent() == null || row.allocationPercent() == null) continue;
            if (drawdown.compareTo(row.drawdownThresholdPercent()) >= 0) {
                if (bestThreshold == null || row.drawdownThresholdPercent().compareTo(bestThreshold) > 0) {
                    bestThreshold = row.drawdownThresholdPercent();
                    best = row.allocationPercent();
                }
            }
        }
        return best;
    }

    // ─────────────────────────── reference high ───────────────────────────

    /** A dated, quality-tagged close used to compute a reference high from history. */
    public record HistoricalClose(LocalDate date, BigDecimal close, DataQuality quality) {}

    /**
     * Compute the reference high from history per the selected mode, considering ONLY valid (OK,
     * positive) closes. Returns null when no valid close qualifies (caller then blocks the calc).
     * For {@link ReferenceHighMode#MANUAL} the caller supplies the value directly (not computed here).
     */
    public BigDecimal referenceHigh(ReferenceHighMode mode, List<HistoricalClose> closesAsc,
                                    LocalDate customStart, int rolling52wSessions) {
        if (mode == ReferenceHighMode.MANUAL) return null; // manual value comes from the strategy, not history
        if (closesAsc == null || closesAsc.isEmpty()) return null;

        List<HistoricalClose> window = switch (mode) {
            case ALL_TIME -> closesAsc;
            case CUSTOM_START_DATE -> customStart == null ? closesAsc
                    : closesAsc.stream().filter(c -> c.date() != null && !c.date().isBefore(customStart)).toList();
            case ROLLING_52_WEEK -> {
                int n = rolling52wSessions > 0 ? rolling52wSessions : 252;
                int from = Math.max(0, closesAsc.size() - n);
                yield closesAsc.subList(from, closesAsc.size());
            }
            case MANUAL -> closesAsc; // unreachable (handled above)
        };

        BigDecimal high = null;
        for (HistoricalClose c : window) {
            if (c.quality() != DataQuality.OK || c.close() == null || c.close().signum() <= 0) continue;
            if (high == null || c.close().compareTo(high) > 0) high = c.close();
        }
        return high;
    }

    // ─────────────────────────── rebalance ───────────────────────────

    /**
     * Compute the rebalance proposal. Blocks (no proposal) when portfolio value is not usable or the
     * target allocation / ETF price are invalid. {@code fxRateEtfToBase} converts trading→base; a
     * null/nonpositive FX rate blocks (never assumes 1).
     *
     * @param portfolioValueBase    portfolio value in base currency (must be > 0 and OK)
     * @param portfolioQuality      quality of the portfolio value
     * @param currentEtfValueBase   current ETF market value in base currency (≥ 0)
     * @param targetAllocationPercent target weight (0–100), typically from {@link #calculate}
     * @param tolerancePercent      absolute allocation tolerance for NO_ACTION
     * @param latestEtfPriceTrading latest valid ETF price in its trading currency (must be > 0 and OK)
     * @param etfPriceQuality       quality of the ETF price
     * @param fxRateEtfToBase       trading→base FX rate (must be > 0)
     * @param quantityScale         decimals for quantity rounding
     * @param fractionalAllowed     whether fractional shares are permitted; if false, quantity floored to whole
     * @param lotSize               lot size (nullable/unknown → treated as 1)
     */
    public RebalanceResult rebalance(
            BigDecimal portfolioValueBase, DataQuality portfolioQuality,
            BigDecimal currentEtfValueBase,
            BigDecimal targetAllocationPercent,
            BigDecimal tolerancePercent,
            BigDecimal latestEtfPriceTrading, DataQuality etfPriceQuality,
            BigDecimal fxRateEtfToBase,
            int quantityScale, boolean fractionalAllowed, BigDecimal lotSize) {

        if (portfolioQuality != null && portfolioQuality != DataQuality.OK) {
            return RebalanceResult.blocked("Portfolio value is " + portfolioQuality + " — no proposal generated.");
        }
        if (portfolioValueBase == null || portfolioValueBase.signum() <= 0) {
            return RebalanceResult.blocked("Portfolio value is zero, negative, or unavailable — no proposal generated.");
        }
        if (targetAllocationPercent == null || targetAllocationPercent.signum() < 0) {
            return RebalanceResult.blocked("Target allocation is unavailable — no proposal generated.");
        }
        String etfBlock = validate("ETF price", latestEtfPriceTrading, etfPriceQuality);
        if (etfBlock != null) return RebalanceResult.blocked(etfBlock);
        if (fxRateEtfToBase == null || fxRateEtfToBase.signum() <= 0) {
            return RebalanceResult.blocked("FX rate for the ETF currency is unavailable — no proposal generated.");
        }

        BigDecimal current = currentEtfValueBase == null ? BigDecimal.ZERO : currentEtfValueBase;
        BigDecimal targetValue = portfolioValueBase
                .multiply(targetAllocationPercent).divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal difference = targetValue.subtract(current).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal actualAllocation = current
                .divide(portfolioValueBase, PCT_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);

        // Tolerance compares the allocation gap (percentage points) against the tolerance.
        BigDecimal gap = targetAllocationPercent.subtract(actualAllocation).abs();
        BigDecimal tol = nz(tolerancePercent);
        RebalanceAction action;
        if (gap.compareTo(tol) <= 0) {
            action = RebalanceAction.NO_ACTION;
        } else if (difference.signum() > 0) {
            action = RebalanceAction.BUY;
        } else {
            action = RebalanceAction.REDUCE;
        }

        // Quantity from the difference converted to trading currency ÷ ETF price, then rounded to
        // instrument constraints. Residual = the base value that rounding could not place.
        BigDecimal estimatedQuantity = BigDecimal.ZERO;
        BigDecimal residual = BigDecimal.ZERO;
        if (action != RebalanceAction.NO_ACTION) {
            BigDecimal diffTrading = difference.abs().divide(fxRateEtfToBase, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal rawQty = diffTrading.divide(latestEtfPriceTrading, quantityScale + 4, RoundingMode.HALF_UP);
            BigDecimal roundedQty = roundQuantity(rawQty, quantityScale, fractionalAllowed, lotSize);
            estimatedQuantity = roundedQty;
            // Residual (base) = difference not covered by the rounded quantity.
            BigDecimal placedTrading = roundedQty.multiply(latestEtfPriceTrading);
            BigDecimal placedBase = placedTrading.multiply(fxRateEtfToBase).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            residual = difference.abs().subtract(placedBase).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return new RebalanceResult(false, null, action,
                targetValue, current, difference, actualAllocation.stripTrailingZeros(),
                estimatedQuantity, residual);
    }

    private BigDecimal roundQuantity(BigDecimal rawQty, int quantityScale, boolean fractionalAllowed, BigDecimal lotSize) {
        BigDecimal lot = (lotSize == null || lotSize.signum() <= 0) ? BigDecimal.ONE : lotSize;
        if (!fractionalAllowed) {
            // Whole shares, then snap DOWN to a whole lot multiple so we never propose more than affordable.
            BigDecimal whole = rawQty.setScale(0, RoundingMode.DOWN);
            if (lot.compareTo(BigDecimal.ONE) > 0) {
                BigDecimal lots = whole.divideToIntegralValue(lot);
                return lots.multiply(lot);
            }
            return whole;
        }
        return rawQty.setScale(quantityScale, RoundingMode.DOWN);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private String validatePrice(String label, PricePoint p) {
        if (p == null || p.value() == null) return label + " is missing — no calculation performed.";
        if (p.quality() != DataQuality.OK) return label + " is " + p.quality() + " — no calculation performed.";
        if (p.value().signum() <= 0) return label + " is nonpositive — no calculation performed.";
        return null;
    }

    private String validate(String label, BigDecimal value, DataQuality quality) {
        if (value == null) return label + " is missing — no proposal generated.";
        if (quality != null && quality != DataQuality.OK) return label + " is " + quality + " — no proposal generated.";
        if (value.signum() <= 0) return label + " is nonpositive — no proposal generated.";
        return null;
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
