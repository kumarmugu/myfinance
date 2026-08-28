package com.myfinance.saas.payment.webhook;

import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.PaymentTransaction;
import com.myfinance.saas.domain.Plan;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.PaymentStatus;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.integration.FinanceAppException;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.PaymentTransactionRepository;
import com.myfinance.saas.repository.ProcessedWebhookEventRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import com.myfinance.saas.subscription.SubscriptionStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock private ProcessedWebhookEventRepository processedRepo;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private PaymentTransactionRepository paymentRepository;
    @Mock private FinanceAppClient financeAppClient;
    @Mock private com.myfinance.saas.email.EmailService emailService;
    @Mock private com.myfinance.saas.observability.AuditLogger auditLogger;
    // Use the real state machine to exercise real transition rules.
    @Spy private SubscriptionStateMachine stateMachine = new SubscriptionStateMachine();

    @InjectMocks private WebhookService webhookService;

    private Customer customer;
    private Subscription subscription;

    @BeforeEach
    void setup() {
        customer = Customer.builder().id(1L).email("member@example.com").build();
        Plan plan = Plan.builder().id(1L).code("pro").enabledFeatures("PORTFOLIO,CRYPTO").build();
        subscription = Subscription.builder().id(10L).customer(customer).plan(plan)
                .state(SubscriptionState.TRIAL).stripeSubscriptionId("sub_123").build();
    }

    private WebhookEvent event(String id, String type) {
        return WebhookEvent.builder()
                .eventId(id).type(type)
                .providerSubscriptionId("sub_123")
                .customerId(1L)
                .amount(new BigDecimal("9.90")).currency("SGD").method("card")
                .paymentReference("pi_1").build();
    }

    @Test
    void paymentSucceededActivatesTrialSubscription() {
        when(processedRepo.existsByEventId("evt_1")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(subscription));
        when(financeAppClient.isConfigured()).thenReturn(true);

        boolean processed = webhookService.process(event("evt_1", "invoice.paid"));

        assertTrue(processed);
        assertEquals(SubscriptionState.ACTIVE, subscription.getState());
        verify(financeAppClient).updateAccess(eq("member@example.com"), eq(true), eq("PORTFOLIO,CRYPTO"));

        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository).save(tx.capture());
        assertEquals(PaymentStatus.SUCCEEDED, tx.getValue().getStatus());
    }

    @Test
    void duplicateEventIsIgnored() {
        when(processedRepo.existsByEventId("evt_dup")).thenReturn(true);

        boolean processed = webhookService.process(event("evt_dup", "invoice.paid"));

        assertFalse(processed);
        verify(subscriptionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(financeAppClient, never()).updateAccess(anyString(), anyBoolean(), any());
    }

    @Test
    void paymentFailedMovesActiveToPastDueButKeepsAccess() {
        subscription.setState(SubscriptionState.ACTIVE);
        when(processedRepo.existsByEventId("evt_2")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(subscription));

        webhookService.process(event("evt_2", "invoice.payment_failed"));

        assertEquals(SubscriptionState.PAST_DUE, subscription.getState());
        // PAST_DUE retains access, so no suspend call is made here.
        verify(financeAppClient, never()).updateAccess(anyString(), eq(false), any());
        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository).save(tx.capture());
        assertEquals(PaymentStatus.FAILED, tx.getValue().getStatus());
    }

    @Test
    void subscriptionDeletedExpiresAndSuspendsAccess() {
        subscription.setState(SubscriptionState.ACTIVE);
        when(processedRepo.existsByEventId("evt_3")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(subscription));
        when(financeAppClient.isConfigured()).thenReturn(true);

        WebhookEvent e = WebhookEvent.builder()
                .eventId("evt_3").type("customer.subscription.deleted")
                .providerSubscriptionId("sub_123").customerId(1L).build();
        webhookService.process(e);

        assertEquals(SubscriptionState.EXPIRED, subscription.getState());
        verify(financeAppClient).updateAccess(eq("member@example.com"), eq(false), any());
    }

    @Test
    void financeAppFailureDoesNotBreakProcessing() {
        when(processedRepo.existsByEventId("evt_4")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(subscription));
        when(financeAppClient.isConfigured()).thenReturn(true);
        doThrow(new FinanceAppException("down")).when(financeAppClient).updateAccess(anyString(), anyBoolean(), any());

        boolean processed = webhookService.process(event("evt_4", "invoice.paid"));

        // Subscription still activated even though the access push failed.
        assertTrue(processed);
        assertEquals(SubscriptionState.ACTIVE, subscription.getState());
    }

    @Test
    void unresolvableSubscriptionIsSafe() {
        when(processedRepo.existsByEventId("evt_5")).thenReturn(false);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.empty());
        when(subscriptionRepository.findByCustomerId(1L)).thenReturn(Optional.empty());

        boolean processed = webhookService.process(event("evt_5", "invoice.paid"));

        assertTrue(processed); // recorded for idempotency
        verify(paymentRepository, never()).save(any());
    }
}
