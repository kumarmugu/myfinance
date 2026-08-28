package com.myfinance.saas.payment.webhook;

import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.PaymentTransaction;
import com.myfinance.saas.domain.ProcessedWebhookEvent;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.PaymentStatus;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.email.EmailService;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.integration.FinanceAppException;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.PaymentTransactionRepository;
import com.myfinance.saas.repository.ProcessedWebhookEventRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import com.myfinance.saas.subscription.SubscriptionStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applies VERIFIED payment webhook events to subscription state. This is the ONLY place a paid
 * subscription becomes ACTIVE — never from a browser-reported success.
 *
 * Guarantees:
 * - Idempotency: each provider event id is processed at most once (ProcessedWebhookEvent).
 * - Consistency: state changes go through the SubscriptionStateMachine.
 * - Resilience: finance-app access push failures are logged for reconciliation, not fatal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final ProcessedWebhookEventRepository processedRepo;
    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final SubscriptionStateMachine stateMachine;
    private final FinanceAppClient financeAppClient;
    private final EmailService emailService;
    private final com.myfinance.saas.observability.AuditLogger auditLogger;

    /**
     * Process a verified event. Returns true if newly processed, false if it was a duplicate.
     */
    @Transactional
    public boolean process(WebhookEvent event) {
        if (event.getEventId() == null) {
            log.warn("Ignoring webhook with no event id");
            return false;
        }
        if (processedRepo.existsByEventId(event.getEventId())) {
            log.info("Duplicate webhook event {} ignored (idempotent)", event.getEventId());
            return false;
        }

        // Record processing first so a mid-handler retry won't double-apply within the same tx.
        processedRepo.save(ProcessedWebhookEvent.builder()
                .eventId(event.getEventId()).eventType(event.getType()).build());

        switch (event.getType()) {
            case "checkout.session.completed", "invoice.paid", "invoice.payment_succeeded" ->
                    handlePaymentSucceeded(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            case "customer.subscription.deleted" -> handleSubscriptionCancelled(event);
            default -> log.info("Webhook event {} type {} recorded, no state change", event.getEventId(), event.getType());
        }
        return true;
    }

    private void handlePaymentSucceeded(WebhookEvent event) {
        Subscription sub = resolveSubscription(event).orElse(null);
        if (sub == null) {
            log.warn("Payment succeeded but no subscription resolved for event {}", event.getEventId());
            return;
        }
        linkProviderRefs(sub, event);

        // TRIAL/PAST_DUE/EXPIRED -> ACTIVE (state machine allows these transitions).
        if (sub.getState() != SubscriptionState.ACTIVE) {
            stateMachine.transition(sub, SubscriptionState.ACTIVE);
        }
        subscriptionRepository.save(sub);

        recordPayment(sub, event, PaymentStatus.SUCCEEDED, null);
        pushAccess(sub, true);
        notifyPaymentSucceeded(sub, event);
        auditLogger.record(com.myfinance.saas.observability.AuditLogger.Event.PAYMENT_SUCCEEDED,
                sub.getCustomer() != null ? sub.getCustomer().getId() : null, "-> ACTIVE");
        log.info("Payment succeeded applied: subscriptionId={} now ACTIVE", sub.getId());
    }

    private void handlePaymentFailed(WebhookEvent event) {
        Subscription sub = resolveSubscription(event).orElse(null);
        if (sub == null) {
            log.warn("Payment failed but no subscription resolved for event {}", event.getEventId());
            return;
        }
        linkProviderRefs(sub, event);

        if (sub.getState() == SubscriptionState.ACTIVE) {
            stateMachine.transition(sub, SubscriptionState.PAST_DUE);
            subscriptionRepository.save(sub);
        }
        recordPayment(sub, event, PaymentStatus.FAILED, "Payment failed");
        safeEmail(() -> emailService.sendPaymentFailed(
                sub.getCustomer().getEmail(), "/portal/billing"));
        // Access is retained during the grace window (PAST_DUE grants access).
        log.info("Payment failed applied: subscriptionId={} now {}", sub.getId(), sub.getState());
    }

    private void handleSubscriptionCancelled(WebhookEvent event) {
        Subscription sub = resolveSubscription(event).orElse(null);
        if (sub == null) {
            return;
        }
        // Provider reports the subscription is gone -> EXPIRED (access removed).
        if (sub.getState() != SubscriptionState.EXPIRED) {
            // Move to CANCELLED first if needed, then EXPIRED, to honour the state machine.
            if (sub.getState() == SubscriptionState.ACTIVE || sub.getState() == SubscriptionState.PAST_DUE
                    || sub.getState() == SubscriptionState.TRIAL) {
                stateMachine.transition(sub, SubscriptionState.CANCELLED);
            }
            stateMachine.transition(sub, SubscriptionState.EXPIRED);
            subscriptionRepository.save(sub);
        }
        pushAccess(sub, false);
        safeEmail(() -> emailService.sendSubscriptionCancelled(
                sub.getCustomer().getEmail(),
                sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd().toLocalDate().toString() : null));
        log.info("Subscription cancellation applied: subscriptionId={} now EXPIRED", sub.getId());
    }

    private Optional<Subscription> resolveSubscription(WebhookEvent event) {
        if (event.getProviderSubscriptionId() != null) {
            Optional<Subscription> byStripe = subscriptionRepository.findByStripeSubscriptionId(event.getProviderSubscriptionId());
            if (byStripe.isPresent()) {
                return byStripe;
            }
        }
        if (event.getCustomerId() != null) {
            return subscriptionRepository.findByCustomerId(event.getCustomerId());
        }
        return Optional.empty();
    }

    private void linkProviderRefs(Subscription sub, WebhookEvent event) {
        if (event.getProviderSubscriptionId() != null && sub.getStripeSubscriptionId() == null) {
            sub.setStripeSubscriptionId(event.getProviderSubscriptionId());
        }
        if (event.getProviderCustomerId() != null) {
            Customer c = sub.getCustomer();
            if (c != null && c.getStripeCustomerId() == null) {
                c.setStripeCustomerId(event.getProviderCustomerId());
                customerRepository.save(c);
            }
        }
    }

    private void recordPayment(Subscription sub, WebhookEvent event, PaymentStatus status, String failureReason) {
        paymentRepository.save(PaymentTransaction.builder()
                .customer(sub.getCustomer())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .status(status)
                .method(event.getMethod())
                .providerReference(event.getPaymentReference())
                .receiptUrl(event.getReceiptUrl())
                .failureReason(failureReason)
                .build());
    }

    /** Push access status to the finance app; non-fatal on failure (reconciliation retries). */
    private void pushAccess(Subscription sub, boolean active) {
        Customer customer = sub.getCustomer();
        if (customer == null || !financeAppClient.isConfigured()) {
            return;
        }
        try {
            String features = sub.getPlan() != null ? sub.getPlan().getEnabledFeatures() : null;
            financeAppClient.updateAccess(customer.getEmail(), active, features);
        } catch (FinanceAppException e) {
            log.error("Failed to push access (active={}) to finance app for customerId={}: {}",
                    active, customer.getId(), e.getMessage());
        }
    }

    private void notifyPaymentSucceeded(Subscription sub, WebhookEvent event) {
        Customer customer = sub.getCustomer();
        if (customer == null) {
            return;
        }
        String planName = sub.getPlan() != null ? sub.getPlan().getName() : "your plan";
        String amount = event.getAmount() != null ? event.getAmount().toPlainString() : "";
        String currency = event.getCurrency() != null ? event.getCurrency() : "";
        safeEmail(() -> emailService.sendPaymentSucceeded(customer.getEmail(), planName, amount, currency));
    }

    /** Email delivery must never break webhook processing. */
    private void safeEmail(Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            log.error("Notification email failed (non-fatal): {}", e.getMessage());
        }
    }
}
