package com.myfinance.saas.scheduling;

import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.Plan;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.email.EmailService;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.integration.FinanceAppException;
import com.myfinance.saas.observability.AuditLogger;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvisioningReconciliationServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private FinanceAppClient financeAppClient;
    @Mock private EmailService emailService;
    @Mock private AuditLogger auditLogger;

    private ProvisioningReconciliationService service() {
        return new ProvisioningReconciliationService(customerRepository, subscriptionRepository,
                financeAppClient, emailService, new AppProperties(), auditLogger);
    }

    private Customer pending(Long id) {
        return Customer.builder().id(id).email("p" + id + "@example.com").fullName("P" + id).provisioned(false).build();
    }

    @Test
    void skipsWhenNotConfigured() {
        when(financeAppClient.isConfigured()).thenReturn(false);
        assertEquals(0, service().reconcile());
        verify(customerRepository, never()).findByProvisionedFalse();
    }

    @Test
    void provisionsPendingCustomersAndMarksProvisioned() {
        Customer c = pending(1L);
        Plan plan = Plan.builder().enabledFeatures("PORTFOLIO").build();
        when(financeAppClient.isConfigured()).thenReturn(true);
        when(customerRepository.findByProvisionedFalse()).thenReturn(List.of(c));
        when(subscriptionRepository.findByCustomerId(1L))
                .thenReturn(Optional.of(Subscription.builder().plan(plan).build()));
        when(financeAppClient.provisionUser(anyString(), any(), eq("PORTFOLIO")))
                .thenReturn(new FinanceAppClient.ProvisionResult(10L, true));

        int provisioned = service().reconcile();

        assertEquals(1, provisioned);
        assertTrue(c.isProvisioned());
        verify(emailService).sendWelcomeEmail(eq("p1@example.com"), any());
        verify(auditLogger).record(eq(AuditLogger.Event.PROVISIONED), eq(1L), anyString());
    }

    @Test
    void leavesCustomerUnprovisionedWhenFinanceAppStillDown() {
        Customer c = pending(2L);
        when(financeAppClient.isConfigured()).thenReturn(true);
        when(customerRepository.findByProvisionedFalse()).thenReturn(List.of(c));
        when(subscriptionRepository.findByCustomerId(2L)).thenReturn(Optional.empty());
        when(financeAppClient.provisionUser(anyString(), any(), any()))
                .thenThrow(new FinanceAppException("still down"));

        int provisioned = service().reconcile();

        assertEquals(0, provisioned);
        assertFalse(c.isProvisioned());
        verify(auditLogger).record(eq(AuditLogger.Event.PROVISIONING_FAILED), eq(2L), anyString());
        verify(emailService, never()).sendWelcomeEmail(anyString(), any());
    }
}
