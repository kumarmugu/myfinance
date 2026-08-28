package com.myfinance.saas.payment;

/**
 * Raised on payment-provider errors. Messages are client-safe (no provider internals).
 */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
