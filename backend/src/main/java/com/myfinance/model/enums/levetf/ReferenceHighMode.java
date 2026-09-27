package com.myfinance.model.enums.levetf;

/** How the benchmark reference high (the peak the drawdown is measured from) is determined. */
public enum ReferenceHighMode {
    /** Maximum valid close across all available history. */
    ALL_TIME,
    /** Maximum valid close across the most recent ~252 trading sessions. */
    ROLLING_52_WEEK,
    /** Maximum valid close since a user-configured start date. */
    CUSTOM_START_DATE,
    /** A user-entered reference value and date; edits are audited. */
    MANUAL
}
