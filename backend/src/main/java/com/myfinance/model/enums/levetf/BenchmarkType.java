package com.myfinance.model.enums.levetf;

/** What kind of series a benchmark is. Documented so reference-high / drawdown math stays consistent. */
public enum BenchmarkType {
    /** A price index (no dividends reinvested). */
    PRICE_INDEX,
    /** A total-return index (dividends reinvested). */
    TOTAL_RETURN_INDEX,
    /** An ETF standing in for an index the provider doesn't cover directly. */
    ETF_PROXY
}
