package com.myfinance.saas.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.enums.CustomerStatus;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private RateLimiter rateLimiter;

    private Customer customer;

    @BeforeEach
    void setup() {
        rateLimiter.reset();
        customerRepository.findByEmail("member@example.com").ifPresent(customerRepository::delete);
        customer = customerRepository.save(Customer.builder()
                .email("member@example.com")
                .passwordHash(passwordEncoder.encode("password1"))
                .fullName("Member One")
                .status(CustomerStatus.ACTIVE)
                .emailVerified(true)
                .build());
    }

    @Test
    void loginSucceedsWithValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com", "password", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.customerId", is(customer.getId().intValue())))
                .andExpect(jsonPath("$.email", is("member@example.com")));
    }

    @Test
    void loginFailsWithWrongPasswordUniformMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com", "password", "wrongpass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid email or password")));
    }

    @Test
    void loginFailsWithUnknownEmailSameMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "nobody@example.com", "password", "password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid email or password")));
    }

    @Test
    void forgotPasswordAlwaysReturnsUniformMessage() throws Exception {
        // Existing email
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If the email exists")));

        // Unknown email — identical response
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "ghost@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If the email exists")));
    }

    @Test
    void resetPasswordWithValidTokenChangesPassword() throws Exception {
        String raw = tokenService.issue(customer.getId(), OneTimeToken.Purpose.PASSWORD_RESET);

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", raw, "newPassword", "newpass123"))))
                .andExpect(status().isOk());

        // New password now works
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com", "password", "newpass123"))))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordRejectsWeakPassword() throws Exception {
        String raw = tokenService.issue(customer.getId(), OneTimeToken.Purpose.PASSWORD_RESET);
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", raw, "newPassword", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordRejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "bogus-token", "newPassword", "newpass123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmailWithValidTokenSucceeds() throws Exception {
        Customer pending = customerRepository.save(Customer.builder()
                .email("pending@example.com").passwordHash(passwordEncoder.encode("password1"))
                .status(CustomerStatus.PENDING_VERIFICATION).emailVerified(false).build());
        String raw = tokenService.issue(pending.getId(), OneTimeToken.Purpose.EMAIL_VERIFICATION);

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", raw))))
                .andExpect(status().isOk());

        Customer reloaded = customerRepository.findById(pending.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(reloaded.isEmailVerified());
        org.junit.jupiter.api.Assertions.assertEquals(CustomerStatus.ACTIVE, reloaded.getStatus());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentCustomerWithValidToken() throws Exception {
        String token = objectMapper.readTree(
                mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com", "password", "password1"))))
                        .andReturn().getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("member@example.com")));
    }

    @Test
    void loginIsRateLimited() throws Exception {
        // 10 allowed per minute; the 11th should be blocked.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com", "password", "wrongpass1"))));
        }
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "member@example.com", "password", "wrongpass1"))))
                .andExpect(status().isTooManyRequests());
    }
}
