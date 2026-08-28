package com.myfinance.saas.payment.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the webhook endpoint fails closed on missing/invalid signatures. Real event
 * processing is covered by WebhookServiceTest (unit) since generating a valid Stripe
 * signature requires the shared secret + HMAC over the exact payload.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void rejectsMissingSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"evt_1\",\"type\":\"invoice.paid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", "t=123,v1=deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"evt_1\",\"type\":\"invoice.paid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhookEndpointIsPubliclyReachableWithoutAuth() throws Exception {
        // No JWT provided; should reach the controller (and fail on signature, not 401/403).
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
