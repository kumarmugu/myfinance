package com.myfinance.saas.payment;

import lombok.Builder;
import lombok.Data;

/**
 * Provider-agnostic parameters for creating a checkout session.
 */
@Data
@Builder
public class CheckoutRequest {
    private Long customerId;
    private String customerEmail;
    /** Existing provider customer id, if any (e.g. Stripe cus_...). May be null. */
    private String providerCustomerId;
    /** Provider price id for the selected plan (e.g. Stripe price_...). */
    private String providerPriceId;
    /** "card" or "paynow". */
    private String method;
    private String successUrl;
    private String cancelUrl;
    /** Idempotency key so retries do not create duplicate sessions/charges. */
    private String idempotencyKey;
}
