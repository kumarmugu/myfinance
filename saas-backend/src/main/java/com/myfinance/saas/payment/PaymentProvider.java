package com.myfinance.saas.payment;

/**
 * Provider-agnostic payment abstraction. Stripe is the initial implementation; additional
 * providers can be added without touching callers. The provider handles the sensitive parts
 * (card/PayNow collection) on its own hosted UI so the SaaS backend never sees card data.
 */
public interface PaymentProvider {

    /** Machine name, e.g. "stripe". Used to select the active provider by config. */
    String name();

    /**
     * Create a hosted checkout session for a subscription. The returned URL is where the
     * customer completes payment (card or PayNow) on the provider's secure UI.
     *
     * @param request checkout parameters (customer, plan, method, return URLs)
     * @return the checkout session details (redirect URL + provider session id)
     */
    CheckoutSession createCheckoutSession(CheckoutRequest request);

    /**
     * Request cancellation of a provider subscription. Whether it cancels immediately or at
     * period end is determined by the provider configuration; the authoritative state change
     * is applied when the corresponding webhook is received.
     *
     * @param providerSubscriptionId provider's subscription id (e.g. Stripe sub_...)
     * @param atPeriodEnd            true to cancel at period end, false for immediate
     */
    void cancelSubscription(String providerSubscriptionId, boolean atPeriodEnd);
}
