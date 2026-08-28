package com.myfinance.saas.domain;

import com.myfinance.saas.domain.enums.CustomerStatus;
import com.myfinance.saas.domain.enums.PaymentStatus;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the JPA mappings and repository queries for the SaaS domain entities.
 * Uses the in-memory H2 test database (schema auto-created).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainPersistenceTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentTransactionRepository paymentRepository;
    @Autowired private ProcessedWebhookEventRepository webhookRepository;

    @Test
    void persistsCustomerSubscriptionPlanAndPayment() {
        Plan plan = planRepository.save(Plan.builder()
                .code("pro").name("Pro").enabledFeatures("PORTFOLIO,CRYPTO")
                .priceAmount(new BigDecimal("9.90")).currency("SGD").build());

        Customer customer = customerRepository.save(Customer.builder()
                .email("jane@example.com").passwordHash("$2a$hash").fullName("Jane")
                .status(CustomerStatus.PENDING_VERIFICATION).build());

        Subscription sub = subscriptionRepository.save(Subscription.builder()
                .customer(customer).plan(plan).state(SubscriptionState.TRIAL)
                .trialEndsAt(LocalDateTime.now().plusDays(7)).build());

        PaymentTransaction tx = paymentRepository.save(PaymentTransaction.builder()
                .customer(customer).amount(new BigDecimal("9.90")).currency("SGD")
                .status(PaymentStatus.SUCCEEDED).method("card").providerReference("pi_123").build());

        assertNotNull(customer.getId());
        assertNotNull(sub.getId());
        assertNotNull(tx.getId());
        assertEquals(SubscriptionState.TRIAL, subscriptionRepository.findByCustomerId(customer.getId()).orElseThrow().getState());
        assertTrue(customerRepository.existsByEmail("jane@example.com"));
        assertEquals(1, paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).size());
        assertEquals("pro", planRepository.findByCode("pro").orElseThrow().getCode());
    }

    @Test
    void webhookEventIdempotencyLookup() {
        webhookRepository.save(ProcessedWebhookEvent.builder().eventId("evt_1").eventType("invoice.paid").build());
        assertTrue(webhookRepository.existsByEventId("evt_1"));
        assertFalse(webhookRepository.existsByEventId("evt_missing"));
    }

    @Test
    void findsExpiredTrials() {
        Customer c = customerRepository.save(Customer.builder()
                .email("expired@example.com").passwordHash("x").build());
        subscriptionRepository.save(Subscription.builder()
                .customer(c).state(SubscriptionState.TRIAL)
                .trialEndsAt(LocalDateTime.now().minusDays(1)).build());

        assertEquals(1, subscriptionRepository
                .findByStateAndTrialEndsAtBefore(SubscriptionState.TRIAL, LocalDateTime.now()).size());
    }
}
