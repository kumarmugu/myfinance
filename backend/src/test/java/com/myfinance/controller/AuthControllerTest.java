package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldLoginSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("username", "user", "password", "test123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.userId", notNullValue()));
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("username", "user", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSelfRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("username", "newuser", "email", "new@test.com", "password", "pass123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void shouldGetCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.username", is("user"))).andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    @WithMockUser
    void shouldChangePassword() throws Exception {
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("currentPassword", "test123", "newPassword", "newpass123"))))
                .andExpect(status().isOk());
        // Change back
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("currentPassword", "newpass123", "newPassword", "test123"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void shouldRejectWrongCurrentPassword() throws Exception {
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("currentPassword", "wrong", "newPassword", "new123"))))
                .andExpect(status().isBadRequest());
    }
}
