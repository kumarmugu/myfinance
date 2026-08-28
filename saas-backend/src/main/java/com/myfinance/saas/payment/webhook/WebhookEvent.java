package com.myfinance.saas.payment.webhook;

import lombok.Builder;
import lombok.Data;

/**
 * A normalized, provider-agnostic representation of a verified payment webhook event.
 * Only fields the SaaS backend needs are extracted; nothing sensitive is carried.
 */
@Data
@Builder
public class WebhookEvent {

    /** Provider's unique event id (for idempotency), e.g. Stripe evt_... */
    private String eventId;

    /** Normalized type, e.g. checkout.session.completed, invoice.paid, invoice.payment_failed,
     *  customer.subscription.deleted. */
    private String type;

    /** Our customer id resolved from the event (client_reference_id / metadata), may be null. */
    private Long customerId;

    /** Provider subscription id (e.g. Stripe sub_...), may be null. */
    private String providerSubscriptionId;

    /** Provider customer id (e.g. Stripe cus_...), may be null. */
    private String providerCustomerId;

    /** Payment amount in major units, may be null. */
    private java.math.BigDecimal amount;

    /** Currency code, may be null. */
    private String currency;

    /** Payment method ("card"/"paynow"), may be null. */
    private String method;

    /** Provider reference for the payment (e.g. invoice/payment_intent id), may be null. */
    private String paymentReference;

    /** Hosted receipt/invoice URL, may be null. */
    private String receiptUrl;
}
