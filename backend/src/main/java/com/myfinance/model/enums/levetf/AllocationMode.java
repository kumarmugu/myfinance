package com.myfinance.model.enums.levetf;

/**
 * How a strategy converts an index drawdown into a target ETF allocation. Modes are never silently
 * mixed — a strategy picks exactly one, and the calculation engine records which was used.
 */
public enum AllocationMode {
    /** target = max(initialAllocation, drawdown × multiplier). Initial is the floor at 0% drawdown. */
    INITIAL_PLUS_HALF_DRAWDOWN,
    /** target = max(minimumAllocation, drawdown × multiplier). */
    DRAWDOWN_ONLY_WITH_MIN,
    /** target read from a user-defined ladder of drawdown-threshold → allocation rows. */
    LADDER
}
