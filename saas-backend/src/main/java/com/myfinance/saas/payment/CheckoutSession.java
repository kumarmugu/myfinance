package com.myfinance.saas.payment;

/**
 * Result of creating a hosted checkout session. Contains only non-sensitive references and
 * the redirect URL — no secrets.
 */
public record CheckoutSession(String sessionId, String redirectUrl) {
}
