package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerExtendedTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        // testUser is already created by BaseControllerTest
    }

    // ─── Forgot Password Tests ───

    @Test
    void shouldHandleForgotPasswordWithValidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "test@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email exists, a reset link has been sent")));

        // Verify token was generated
        AppUser user = appUserRepository.findByEmail("test@test.com").orElseThrow();
        assert user.getResetToken() != null;
        assert user.getResetTokenExpiry() != null;
    }

    @Test
    void shouldHandleForgotPasswordWithInvalidEmail() throws Exception {
        // Should return same response to avoid email enumeration
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nonexistent@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email exists, a reset link has been sent")));
    }

    // ─── Reset Password Tests ───

    @Test
    void shouldResetPasswordWithValidToken() throws Exception {
        // Set up a reset token
        String token = UUID.randomUUID().toString();
        AppUser user = appUserRepository.findByUsername("user").orElseThrow();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        appUserRepository.save(user);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", "newpass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Password has been reset successfully")));

        // Verify token was cleared
        AppUser updated = appUserRepository.findByUsername("user").orElseThrow();
        assert updated.getResetToken() == null;
        assert updated.getResetTokenExpiry() == null;
    }

    @Test
    void shouldRejectResetPasswordWithInvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "invalid-token-123", "newPassword", "newpass123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid or expired reset token")));
    }

    @Test
    void shouldRejectResetPasswordWithExpiredToken() throws Exception {
        // Set up an expired token
        String token = UUID.randomUUID().toString();
        AppUser user = appUserRepository.findByUsername("user").orElseThrow();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().minusHours(2)); // expired 2 hours ago
        appUserRepository.save(user);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", "newpass123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Reset token has expired")));
    }

    // ─── Verify Password Tests ───

    @Test
    @WithMockUser
    void shouldVerifyCorrectPassword() throws Exception {
        mockMvc.perform(post("/api/auth/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "test123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    @Test
    @WithMockUser
    void shouldRejectIncorrectPassword() throws Exception {
        mockMvc.perform(post("/api/auth/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "wrongpassword"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)));
    }

    // ─── Get Current User Tests ───

    @Test
    @WithMockUser
    void shouldGetCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("user")))
                .andExpect(jsonPath("$.email", is("test@test.com")))
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    void shouldReturn401ForUnauthenticatedMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Login Tests ───

    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "user", "password", "test123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username", is("user")))
                .andExpect(jsonPath("$.email", is("test@test.com")))
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    void shouldRejectLoginWithInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "user", "password", "wrongpass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid username or password")));
    }

    @Test
    void shouldRejectLoginWithNonexistentUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "nonexistent", "password", "test123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid username or password")));
    }

    // ─── Register Tests ───

    @Test
    void shouldRejectSelfRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "newuser", "email", "new@test.com", "password", "pass123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("disabled")));
    }

    // ─── Change Password Tests ───

    @Test
    @WithMockUser
    void shouldChangePasswordWithCorrectCurrent() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", "test123", "newPassword", "newpass456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Password changed successfully")));

        // Reset password back for other tests
        AppUser user = appUserRepository.findByUsername("user").orElseThrow();
        user.setPassword(passwordEncoder.encode("test123"));
        appUserRepository.save(user);
    }

    @Test
    @WithMockUser
    void shouldRejectChangePasswordWithWrongCurrent() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", "wrongpassword", "newPassword", "newpass456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Current password is incorrect")));
    }
}
