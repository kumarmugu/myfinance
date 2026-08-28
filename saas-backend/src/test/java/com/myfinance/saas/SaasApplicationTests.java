package com.myfinance.saas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke tests: the context loads and the public config endpoint returns only
 * non-sensitive values (never the Stripe secret key).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaasApplicationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void publicConfigReturnsNonSensitiveValues() throws Exception {
        mockMvc.perform(get("/api/public/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trialDays", is(7)))
                .andExpect(jsonPath("$.paymentProvider", is("stripe")))
                .andExpect(jsonPath("$.stripePublishableKey", is("pk_test_dummy")));
    }

    @Test
    void publicConfigDoesNotLeakSecretKey() throws Exception {
        String body = mockMvc.perform(get("/api/public/config"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("sk_test_dummy"),
                "Public config must never contain the Stripe secret key");
    }
}
