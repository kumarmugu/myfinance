package com.myfinance.saas.payment.webhook;

import com.myfinance.saas.config.AppProperties;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Verifies Stripe webhook signatures using the configured webhook secret and normalizes the
 * event. If the signature is invalid, verification fails closed (empty result), so tampered or
 * unauthenticated events are never processed.
 *
 * Uses the raw JSON payload (via a minimal tree read) to avoid coupling to a specific pinned
 * Stripe API object version, which keeps normalization resilient across API versions.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookVerifier implements WebhookVerifier {

    private final AppProperties appProperties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public Optional<WebhookEvent> verifyAndParse(String rawPayload, String signatureHeader) {
        String secret = appProperties.getStripe().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.error("Stripe webhook secret not configured; rejecting webhook");
            return Optional.empty();
        }
        Event event;
        try {
            event = Webhook.constructEvent(rawPayload, signatureHeader, secret);
        } catch (Exception e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return Optional.empty();
        }

        try {
            return Optional.of(normalize(event, rawPayload));
        } catch (Exception e) {
            log.error("Failed to normalize Stripe event {}: {}", event.getId(), e.getMessage());
            // Verified but unparseable: still return a minimal event so idempotency is recorded.
            return Optional.of(WebhookEvent.builder().eventId(event.getId()).type(event.getType()).build());
        }
    }

    private WebhookEvent normalize(Event event, String rawPayload) throws Exception {
        var root = objectMapper.readTree(rawPayload);
        var object = root.path("data").path("object");

        WebhookEvent.WebhookEventBuilder b = WebhookEvent.builder()
                .eventId(event.getId())
                .type(event.getType());

        // client_reference_id carries our customer id (set on checkout session creation).
        String clientRef = object.path("client_reference_id").asText(null);
        if (clientRef != null && clientRef.matches("\\d+")) {
            b.customerId(Long.valueOf(clientRef));
        }

        String subscription = firstNonNull(
                object.path("subscription").asText(null),
                object.path("id").asText(null));
        // For subscription objects, "id" is the subscription id; for invoices, "subscription".
        if ("customer.subscription.updated".equals(event.getType())
                || "customer.subscription.deleted".equals(event.getType())) {
            b.providerSubscriptionId(object.path("id").asText(null));
        } else {
            b.providerSubscriptionId(object.path("subscription").asText(null));
        }

        b.providerCustomerId(object.path("customer").asText(null));

        // Amount (Stripe uses minor units for amount_paid/amount_total).
        long minor = object.path("amount_paid").asLong(object.path("amount_total").asLong(0));
        if (minor > 0) {
            b.amount(BigDecimal.valueOf(minor).movePointLeft(2));
        }
        String currency = object.path("currency").asText(null);
        if (currency != null) b.currency(currency.toUpperCase());

        b.paymentReference(object.path("id").asText(null));
        b.receiptUrl(firstNonNull(
                object.path("hosted_invoice_url").asText(null),
                object.path("receipt_url").asText(null)));

        return b.build();
    }

    private String firstNonNull(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
