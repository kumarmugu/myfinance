package com.myfinance.saas.integration;

/**
 * Raised when the finance-app integration call fails (transport or HTTP error).
 * Signals to callers that the operation may be retried / queued for reconciliation.
 */
public class FinanceAppException extends RuntimeException {
    public FinanceAppException(String message) {
        super(message);
    }
}
