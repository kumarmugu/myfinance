package com.myfinance.model.enums.levetf;

/** Which value the strategy's target allocation percent is applied to. */
public enum PortfolioScopeType {
    /** The user's whole investment portfolio (base currency). */
    WHOLE,
    /** A single selected investment account / broker. */
    ACCOUNT,
    /** A user-entered manual portfolio value (base currency). */
    MANUAL
}
