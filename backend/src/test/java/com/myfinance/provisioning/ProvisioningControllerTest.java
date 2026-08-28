package com.myfinance.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the additive internal provisioning API used by the SaaS platform.
 * Auth is via the X-Provisioning-Token header (ProvisioningTokenFilter), not JWT.
 * Self-contained: does not depend on DataInitializer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProvisioningControllerTest {

    private static final String TOKEN_HEADER = "X-Provisioning-Token";
    private static final String VALID_TOKEN = "test-provisioning-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;

    @BeforeEach
    void cleanup() {
        appUserRepository.findByEmail("saas-customer@example.com").ifPresent(appUserRepository::delete);
    }

    @Test
    void shouldRejectWhenTokenMissing() throws Exception {
        mockMvc.perform(post("/api/internal/provisioning/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "saas-customer@example.com"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWhenTokenInvalid() throws Exception {
        mockMvc.perform(post("/api/internal/provisioning/users")
                .header(TOKEN_HEADER, "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "saas-customer@example.com"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldProvisionUserWithValidToken() throws Exception {
        Map<String, String> request = Map.of(
                "email", "saas-customer@example.com",
                "displayName", "SaaS Customer",
                "enabledFeatures", "PORTFOLIO,DIVIDENDS,REPORTS");

        mockMvc.perform(post("/api/internal/provisioning/users")
                .header(TOKEN_HEADER, VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("saas-customer@example.com")))
                .andExpect(jsonPath("$.enabledFeatures", is("PORTFOLIO,DIVIDENDS,REPORTS")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.created", is(true)))
                .andExpect(jsonPath("$.userId", notNullValue()));

        AppUser created = appUserRepository.findByEmail("saas-customer@example.com").orElseThrow();
        // Never a duplicate; role is USER; password is hashed (not blank).
        org.junit.jupiter.api.Assertions.assertEquals("USER", created.getRole());
        org.junit.jupiter.api.Assertions.assertNotNull(created.getPassword());
        org.junit.jupiter.api.Assertions.assertNotEquals("", created.getPassword());
    }

    @Test
    void shouldBeIdempotentOnRepeatProvisioning() throws Exception {
        Map<String, String> request = Map.of("email", "saas-customer@example.com", "displayName", "SaaS Customer");

        mockMvc.perform(post("/api/internal/provisioning/users")
                .header(TOKEN_HEADER, VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created", is(true)));

        // Second call returns existing user (200, created=false), no duplicate.
        mockMvc.perform(post("/api/internal/provisioning/users")
                .header(TOKEN_HEADER, VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(false)));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                appUserRepository.findAll().stream()
                        .filter(u -> "saas-customer@example.com".equals(u.getEmail())).count());
    }

    @Test
    void shouldUpdateAccessStatus() throws Exception {
        // Provision first
        mockMvc.perform(post("/api/internal/provisioning/users")
                .header(TOKEN_HEADER, VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "saas-customer@example.com"))))
                .andExpect(status().isCreated());

        // Suspend
        Map<String, Object> suspend = Map.of("email", "saas-customer@example.com", "active", false);
        mockMvc.perform(post("/api/internal/provisioning/status")
                .header(TOKEN_HEADER, VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(suspend)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        AppUser user = appUserRepository.findByEmail("saas-customer@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(false, user.getIsActive());
    }

    @Test
    void shouldReturn404ForStatusOfUnknownEmail() throws Exception {
        mockMvc.perform(get("/api/internal/provisioning/users")
                .header(TOKEN_HEADER, VALID_TOKEN)
                .param("email", "nobody@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldValidateEmailFormat() throws Exception {
        mockMvc.perform(post("/api/internal/provisioning/users")
                .header(TOKEN_HEADER, VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest());
    }
}
