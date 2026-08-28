package com.myfinance.saas.signup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.CustomerStatus;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.integration.FinanceAppException;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import com.myfinance.saas.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SignupControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private RateLimiter rateLimiter;

    @MockBean private FinanceAppClient financeAppClient;

    @BeforeEach
    void setup() {
        rateLimiter.reset();
        // Default: provisioning configured and succeeds.
        when(financeAppClient.isConfigured()).thenReturn(true);
        when(financeAppClient.provisionUser(anyString(), any(), any()))
                .thenReturn(new FinanceAppClient.ProvisionResult(100L, true));
    }

    private Map<String, Object> validSignup(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "New Person");
        body.put("email", email);
        body.put("password", "password1");
        body.put("acceptTerms", true);
        return body;
    }

    @Test
    void signupCreatesCustomerTrialAndProvisions() throws Exception {
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignup("newperson@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provisioned", is(true)));

        Customer c = customerRepository.findByEmail("newperson@example.com").orElseThrow();
        assertEqualsStatus(c);
        Subscription sub = subscriptionRepository.findByCustomerId(c.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(SubscriptionState.TRIAL, sub.getState());
        org.junit.jupiter.api.Assertions.assertNotNull(sub.getTrialEndsAt());
        org.junit.jupiter.api.Assertions.assertTrue(c.isProvisioned());

        verify(financeAppClient).provisionUser(eq("newperson@example.com"), any(), any());
    }

    private void assertEqualsStatus(Customer c) {
        org.junit.jupiter.api.Assertions.assertEquals(CustomerStatus.PENDING_VERIFICATION, c.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(c.isEmailVerified());
    }

    @Test
    void signupSucceedsEvenWhenProvisioningFails() throws Exception {
        when(financeAppClient.provisionUser(anyString(), any(), any()))
                .thenThrow(new FinanceAppException("finance app down"));

        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignup("resilient@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provisioned", is(false)));

        // Customer + subscription still created; provisioned=false for later retry.
        Customer c = customerRepository.findByEmail("resilient@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(c.isProvisioned());
        org.junit.jupiter.api.Assertions.assertTrue(subscriptionRepository.findByCustomerId(c.getId()).isPresent());
    }

    @Test
    void duplicateSignupIsEnumerationSafeAndNoDuplicate() throws Exception {
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignup("dupe@example.com"))))
                .andExpect(status().isCreated());

        // Second signup with same email returns the same generic message, no exception, no dup.
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignup("dupe@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", containsString("verify your address")));

        long count = customerRepository.findAll().stream()
                .filter(c -> "dupe@example.com".equals(c.getEmail())).count();
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void signupRequiresTermsAcceptance() throws Exception {
        Map<String, Object> body = validSignup("noterms@example.com");
        body.put("acceptTerms", false);
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupRejectsWeakPassword() throws Exception {
        Map<String, Object> body = validSignup("weak@example.com");
        body.put("password", "short");
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupRejectsInvalidEmail() throws Exception {
        Map<String, Object> body = validSignup("not-an-email");
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void honeypotSilentlyRejectsBots() throws Exception {
        mockMvc.perform(post("/api/signup")
                .header("X-Honeypot", "i-am-a-bot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignup("bot@example.com"))))
                .andExpect(status().isOk());

        // No customer created for the honeypot submission.
        org.junit.jupiter.api.Assertions.assertTrue(customerRepository.findByEmail("bot@example.com").isEmpty());
    }

    @Test
    void signupIsRateLimited() throws Exception {
        // 5 allowed per 10 minutes; 6th blocked.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validSignup("rl" + i + "@example.com"))));
        }
        mockMvc.perform(post("/api/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignup("rl-blocked@example.com"))))
                .andExpect(status().isTooManyRequests());
    }
}
