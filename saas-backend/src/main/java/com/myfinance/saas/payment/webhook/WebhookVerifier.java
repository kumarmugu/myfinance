package com.myfinance.saas.payment.webhook;

import java.util.Optional;

/**
 * Verifies and parses a raw webhook payload for the active payment provider.
 * Returns empty when the signature is invalid or the payload cannot be trusted.
 */
public interface WebhookVerifier {

    /**
     * Verify the signature against the raw request body and parse into a normalized event.
     *
     * @param rawPayload the exact raw request body bytes as a string (must not be re-serialized)
     * @param signatureHeader the provider signature header value
     * @return the normalized event if verification succeeds; empty otherwise
     */
    Optional<WebhookEvent> verifyAndParse(String rawPayload, String signatureHeader);
}
