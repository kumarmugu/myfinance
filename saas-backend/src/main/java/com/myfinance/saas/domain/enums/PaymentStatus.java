package com.myfinance.saas.domain.enums;

/**
 * Status of a recorded payment attempt/transaction (mirrors provider outcomes).
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED
}
