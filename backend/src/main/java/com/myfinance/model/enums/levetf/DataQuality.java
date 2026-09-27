package com.myfinance.model.enums.levetf;

/**
 * Quality of a market-data point. The calculation engine only trusts {@link #OK}; everything else is
 * excluded from calculations and surfaced to the user (a value is never silently replaced with zero).
 */
public enum DataQuality {
    /** Fresh, valid, positive. */
    OK,
    /** Older than the configured freshness window. */
    STALE,
    /** Present but unusable (nonpositive, unparseable). */
    INVALID,
    /** No data available for the requested date/instrument. */
    MISSING
}
