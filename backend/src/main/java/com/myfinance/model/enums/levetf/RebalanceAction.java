package com.myfinance.model.enums.levetf;

/** The action a rebalance proposal suggests. Advisory only — never executed automatically. */
public enum RebalanceAction {
    /** Increase the ETF position toward target. */
    BUY,
    /** Decrease the ETF position toward target (partial sell). */
    REDUCE,
    /** Fully exit / large decrease. */
    SELL,
    /** Within tolerance, or blocked by invalid inputs — do nothing. */
    NO_ACTION
}
