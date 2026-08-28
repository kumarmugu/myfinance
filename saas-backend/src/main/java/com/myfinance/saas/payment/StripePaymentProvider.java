package com.myfinance.saas.payment;

import com.myfinance.saas.config.AppProperties;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stripe implementation of {@link PaymentProvider}.
 *
 * Uses Stripe Checkout in subscription mode. Card and PayNow are collected on Stripe's hosted
 * page, so the SaaS backend never handles card data (minimal PCI scope). The secret key is
 * read from configuration (env/secret store) and never exposed to the browser.
 *
 * Active only when {@code app.payment.provider=stripe} (the default).
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe", matchIfMissing = true)
@Slf4j
public class StripePaymentProvider implements PaymentProvider {

    private final StripeClient stripe;

    public StripePaymentProvider(AppProperties appProperties) {
        String secretKey = appProperties.getStripe().getSecretKey();
        // StripeClient tolerates a placeholder key at construction; real calls require a valid key.
        this.stripe = new StripeClient(secretKey == null || secretKey.isBlank() ? "sk_placeholder" : secretKey);
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        SessionCreateParams.PaymentMethodType method = "paynow".equalsIgnoreCase(request.getMethod())
                ? SessionCreateParams.PaymentMethodType.PAYNOW
                : SessionCreateParams.PaymentMethodType.CARD;

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addPaymentMethodType(method)
                .setSuccessUrl(request.getSuccessUrl())
                .setCancelUrl(request.getCancelUrl())
                .setClientReferenceId(String.valueOf(request.getCustomerId()))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(request.getProviderPriceId())
                        .setQuantity(1L)
                        .build());

        if (request.getProviderCustomerId() != null && !request.getProviderCustomerId().isBlank()) {
            builder.setCustomer(request.getProviderCustomerId());
        } else if (request.getCustomerEmail() != null) {
            builder.setCustomerEmail(request.getCustomerEmail());
        }

        try {
            com.stripe.net.RequestOptions options = request.getIdempotencyKey() != null
                    ? com.stripe.net.RequestOptions.builder().setIdempotencyKey(request.getIdempotencyKey()).build()
                    : null;
            Session session = options != null
                    ? stripe.checkout().sessions().create(builder.build(), options)
                    : stripe.checkout().sessions().create(builder.build());
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (Exception e) {
            log.error("Stripe checkout session creation failed: {}", e.getMessage());
            throw new PaymentException("Unable to start checkout", e);
        }
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId, boolean atPeriodEnd) {
        try {
            if (atPeriodEnd) {
                stripe.subscriptions().update(providerSubscriptionId,
                        SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());
            } else {
                stripe.subscriptions().cancel(providerSubscriptionId);
            }
        } catch (Exception e) {
            log.error("Stripe subscription cancel failed: {}", e.getMessage());
            throw new PaymentException("Unable to cancel subscription", e);
        }
    }
}
