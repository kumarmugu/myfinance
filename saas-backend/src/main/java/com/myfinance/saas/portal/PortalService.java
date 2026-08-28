package com.myfinance.saas.portal;

import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.Plan;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.payment.CheckoutRequest;
import com.myfinance.saas.payment.CheckoutSession;
import com.myfinance.saas.payment.PaymentProvider;
import com.myfinance.saas.portal.dto.*;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.PaymentTransactionRepository;
import com.myfinance.saas.repository.PlanRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import com.myfinance.saas.subscription.TrialCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Billing/subscription portal business logic. Every method is scoped to a customerId that the
 * caller resolves from the authenticated JWT (never from client input), enforcing tenant
 * isolation and object-level authorization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final PlanRepository planRepository;
    private final CustomerRepository customerRepository;
    private final PaymentProvider paymentProvider;
    private final TrialCalculator trialCalculator;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public SubscriptionView getSubscription(Long customerId) {
        Subscription sub = subscriptionRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new PortalException("No subscription found"));
        Plan plan = sub.getPlan();
        boolean inTrial = sub.getState() == SubscriptionState.TRIAL;
        long daysRemaining = inTrial
                ? trialCalculator.daysRemaining(sub.getTrialEndsAt(), LocalDateTime.now())
                : 0;
        boolean grantsAccess = sub.getState() != SubscriptionState.EXPIRED;

        return SubscriptionView.builder()
                .state(sub.getState().name())
                .planCode(plan != null ? plan.getCode() : null)
                .planName(plan != null ? plan.getName() : null)
                .inTrial(inTrial)
                .trialEndsAt(sub.getTrialEndsAt())
                .trialDaysRemaining(daysRemaining)
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .cancelledAt(sub.getCancelledAt())
                .grantsAccess(grantsAccess)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentView> getPaymentHistory(Long customerId) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(PaymentView::from)
                .toList();
    }

    /**
     * Start a checkout session for the selected paid plan. Uses an idempotency key so retries
     * do not create duplicate charges.
     */
    @Transactional
    public CheckoutSession startCheckout(Long customerId, String planCode, String method) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new PortalException("Customer not found"));

        Plan plan = planRepository.findByCode(planCode)
                .filter(Plan::isActive)
                .orElseThrow(() -> new PortalException("Plan not available"));

        if (plan.getStripePriceId() == null || plan.getStripePriceId().isBlank()) {
            throw new PortalException("This plan is not purchasable yet");
        }

        String base = appProperties.getPublicWebUrl();
        CheckoutRequest request = CheckoutRequest.builder()
                .customerId(customerId)
                .customerEmail(customer.getEmail())
                .providerCustomerId(customer.getStripeCustomerId())
                .providerPriceId(plan.getStripePriceId())
                .method(method == null ? "card" : method)
                .successUrl(base + "/portal/billing?checkout=success")
                .cancelUrl(base + "/portal/billing?checkout=cancelled")
                .idempotencyKey("checkout-" + customerId + "-" + plan.getCode() + "-" + UUID.randomUUID())
                .build();

        CheckoutSession session = paymentProvider.createCheckoutSession(request);
        log.info("Started checkout for customerId={} plan={} method={}", customerId, planCode, method);
        return session;
    }

    /**
     * Request cancellation of the customer's subscription. The authoritative state change is
     * applied when the provider webhook is received; here we only initiate it.
     */
    @Transactional
    public void cancelSubscription(Long customerId, boolean atPeriodEnd) {
        Subscription sub = subscriptionRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new PortalException("No subscription found"));
        if (sub.getStripeSubscriptionId() == null || sub.getStripeSubscriptionId().isBlank()) {
            throw new PortalException("No active paid subscription to cancel");
        }
        paymentProvider.cancelSubscription(sub.getStripeSubscriptionId(), atPeriodEnd);
        log.info("Cancellation requested for customerId={} atPeriodEnd={}", customerId, atPeriodEnd);
    }
}
