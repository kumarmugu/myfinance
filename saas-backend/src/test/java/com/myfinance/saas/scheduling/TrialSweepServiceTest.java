package com.myfinance.saas.scheduling;

import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.email.EmailService;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.observability.AuditLogger;
import com.myfinance.saas.repository.SubscriptionRepository;
import com.myfinance.saas.subscription.SubscriptionStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialSweepServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private EmailService emailService;
    @Mock private FinanceAppClient financeAppClient;
    @Mock private AuditLogger auditLogger;
    @Spy private SubscriptionStateMachine stateMachine = new SubscriptionStateMachine();

    private TrialSweepService service() {
        AppProperties props = new AppProperties();
        return new TrialSweepService(subscriptionRepository, stateMachine, emailService,
                financeAppClient, props, auditLogger);
    }

    private Subscription trial(Long id, LocalDateTime endsAt) {
        Customer c = Customer.builder().id(id).email("c" + id + "@example.com").build();
        return Subscription.builder().id(id).customer(c).state(SubscriptionState.TRIAL).trialEndsAt(endsAt).build();
    }

    @Test
    void expiresEndedTrialsAndSuspendsAndEmails() {
        LocalDateTime now = LocalDateTime.now();
        Subscription s = trial(1L, now.minusDays(1));
        when(subscriptionRepository.findByStateAndTrialEndsAtBefore(eq(SubscriptionState.TRIAL), any()))
                .thenReturn(List.of(s));
        when(financeAppClient.isConfigured()).thenReturn(true);

        int expired = service().expireEndedTrials(now);

        assertEquals(1, expired);
        assertEquals(SubscriptionState.EXPIRED, s.getState());
        verify(financeAppClient).updateAccess(eq("c1@example.com"), eq(false), isNull());
        verify(emailService).sendTrialExpired(eq("c1@example.com"), anyString());
        verify(auditLogger).record(eq(AuditLogger.Event.TRIAL_EXPIRED), eq(1L), anyString());
    }

    @Test
    void oneFailingRowDoesNotStopSweep() {
        LocalDateTime now = LocalDateTime.now();
        Subscription good = trial(1L, now.minusDays(1));
        Subscription bad = trial(2L, now.minusDays(1));
        when(subscriptionRepository.findByStateAndTrialEndsAtBefore(eq(SubscriptionState.TRIAL), any()))
                .thenReturn(List.of(bad, good));
        when(financeAppClient.isConfigured()).thenReturn(true);
        // Make the email for the bad one throw to simulate a per-row failure path.
        doThrow(new RuntimeException("boom")).when(subscriptionRepository).save(bad);

        int expired = service().expireEndedTrials(now);

        // good still processed
        assertEquals(1, expired);
        assertEquals(SubscriptionState.EXPIRED, good.getState());
    }

    @Test
    void notifiesEndingSoonTrials() {
        LocalDateTime now = LocalDateTime.now();
        Subscription s = trial(3L, now.plusDays(1));
        when(subscriptionRepository.findByStateAndTrialEndsAtBetween(eq(SubscriptionState.TRIAL), any(), any()))
                .thenReturn(List.of(s));

        int notified = service().notifyEndingSoon(now);

        assertEquals(1, notified);
        verify(emailService).sendTrialEndingSoon(eq("c3@example.com"), anyLong(), anyString());
    }

    @Test
    void expireIsNoOpWhenNoEndedTrials() {
        when(subscriptionRepository.findByStateAndTrialEndsAtBefore(eq(SubscriptionState.TRIAL), any()))
                .thenReturn(List.of());
        assertEquals(0, service().expireEndedTrials(LocalDateTime.now()));
        verifyNoInteractions(emailService);
    }
}
