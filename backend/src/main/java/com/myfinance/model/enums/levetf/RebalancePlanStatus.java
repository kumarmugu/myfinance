package com.myfinance.model.enums.levetf;

/** Lifecycle of a rebalance proposal. Advisory throughout — approval never places a broker order. */
public enum RebalancePlanStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    EXECUTED,
    PARTIALLY_EXECUTED,
    CANCELLED,
    EXPIRED
}
