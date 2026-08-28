package com.myfinance.saas.portal;

import com.myfinance.saas.auth.PortalJwtService;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.PaymentTransaction;
import com.myfinance.saas.domain.Plan;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.CustomerStatus;
import com.myfinance.saas.domain.enums.PaymentStatus;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.payment.CheckoutSession;
import com.myfinance.saas.payment.PaymentProvider;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.PaymentTransactionRepository;
import com.myfinance.saas.repository.PlanRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PortalControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentTransactionRepository paymentRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PortalJwtService jwtService;

    @MockBean private PaymentProvider paymentProvider;

    private Customer alice;
    private Customer bob;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setup() {
        // Clean dependents before customers to satisfy FK constraints across shared context.
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        customerRepository.findByEmail("alice@example.com").ifPresent(customerRepository::delete);
        customerRepository.findByEmail("bob@example.com").ifPresent(customerRepository::delete);

        Plan proPaid = planRepository.findByCode("portal-pro").orElseGet(() -> planRepository.save(
                Plan.builder().code("portal-pro").name("Portal Pro").enabledFeatures("PORTFOLIO")
                        .priceAmount(new BigDecimal("9.90")).currency("SGD")
                        .stripePriceId("price_123").active(true).build()));

        alice = customerRepository.save(Customer.builder()
                .email("alice@example.com").passwordHash(passwordEncoder.encode("password1"))
                .status(CustomerStatus.ACTIVE).emailVerified(true).build());
        bob = customerRepository.save(Customer.builder()
                .email("bob@example.com").passwordHash(passwordEncoder.encode("password1"))
                .status(CustomerStatus.ACTIVE).emailVerified(true).build());

        subscriptionRepository.save(Subscription.builder()
                .customer(alice).plan(proPaid).state(SubscriptionState.TRIAL)
                .trialEndsAt(LocalDateTime.now().plusDays(5)).build());
        subscriptionRepository.save(Subscription.builder()
                .customer(bob).plan(proPaid).state(SubscriptionState.ACTIVE)
                .stripeSubscriptionId("sub_bob").build());

        paymentRepository.save(PaymentTransaction.builder()
                .customer(alice).amount(new BigDecimal("9.90")).currency("SGD")
                .status(PaymentStatus.SUCCEEDED).method("card").providerReference("pi_alice").build());
        paymentRepository.save(PaymentTransaction.builder()
                .customer(bob).amount(new BigDecimal("9.90")).currency("SGD")
                .status(PaymentStatus.SUCCEEDED).method("card").providerReference("pi_bob").build());

        aliceToken = jwtService.generateToken(alice.getId(), alice.getEmail());
        bobToken = jwtService.generateToken(bob.getId(), bob.getEmail());
    }

    @Test
    void subscriptionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/portal/subscription"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOwnSubscriptionWithTrialInfo() throws Exception {
        mockMvc.perform(get("/api/portal/subscription").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("TRIAL")))
                .andExpect(jsonPath("$.inTrial", is(true)))
                .andExpect(jsonPath("$.trialDaysRemaining", greaterThan(0)))
                .andExpect(jsonPath("$.grantsAccess", is(true)));
    }

    @Test
    void paymentHistoryIsScopedToOwnCustomerOnly() throws Exception {
        // Alice sees only her own payment (pi_alice), never Bob's (pi_bob).
        String body = mockMvc.perform(get("/api/portal/payments").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("pi_bob"),
                "Payment history must not leak another customer's transactions");
    }

    @Test
    void checkoutReturnsRedirectUrl() throws Exception {
        when(paymentProvider.createCheckoutSession(any()))
                .thenReturn(new CheckoutSession("cs_test_1", "https://checkout.stripe.test/cs_test_1"));

        mockMvc.perform(post("/api/portal/checkout")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("planCode", "portal-pro", "method", "card"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl", containsString("checkout.stripe.test")));
    }

    @Test
    void cancelInitiatesProviderCancellation() throws Exception {
        // Bob has an active paid subscription with a stripe id.
        mockMvc.perform(post("/api/portal/cancel").header("Authorization", "Bearer " + bobToken)
                .param("atPeriodEnd", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Cancellation requested")));
    }

    @Test
    void cancelWithoutPaidSubscriptionReturns400() throws Exception {
        // Alice is on trial with no stripe subscription id.
        mockMvc.perform(post("/api/portal/cancel").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicPlansEndpointListsActivePlans() throws Exception {
        mockMvc.perform(get("/api/public/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));
    }
}
