package com.myfinance.model.enums.levetf;

/** What condition an alert preference watches for. */
public enum AlertTrigger {
    DRAWDOWN_THRESHOLD,
    TARGET_CHANGE,
    GAP_EXCEEDS,
    NEW_HIGH,
    STALE_DATA,
    PLAN_PENDING
}
