package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
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
class UserManagementControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private AppUser adminUser;

    @BeforeEach
    void setup() {
        // Create admin user if not exists
        if (appUserRepository.findByUsername("admin").isEmpty()) {
            adminUser = appUserRepository.save(AppUser.builder()
                    .username("admin")
                    .email("admin@test.com")
                    .password(passwordEncoder.encode("admin123"))
                    .displayName("Admin User")
                    .role("ADMIN")
                    .build());
        } else {
            adminUser = appUserRepository.findByUsername("admin").get();
        }
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldListAllUsersForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].password", everyItem(nullValue())));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldReturn403ForNonAdminListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldCreateNewUser() throws Exception {
        // Clean up if exists from previous run
        appUserRepository.findByUsername("newuser").ifPresent(appUserRepository::delete);

        Map<String, String> request = Map.of(
                "username", "newuser",
                "email", "newuser@test.com",
                "password", "password123",
                "displayName", "New User",
                "role", "USER"
        );

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("newuser")))
                .andExpect(jsonPath("$.email", is("newuser@test.com")))
                .andExpect(jsonPath("$.displayName", is("New User")))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user")
    void shouldReturn403ForNonAdminCreateUser() throws Exception {
        Map<String, String> request = Map.of(
                "username", "hacker",
                "email", "hacker@test.com",
                "password", "hack123"
        );

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldRejectDuplicateUsername() throws Exception {
        Map<String, String> request = Map.of(
                "username", "user",
                "email", "another@test.com",
                "password", "password123"
        );

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("already taken")));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldToggleUserActiveStatus() throws Exception {
        // testUser is active by default
        Boolean originalStatus = testUser.getIsActive();

        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/toggle-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(!originalStatus)));

        // Toggle back
        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/toggle-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(originalStatus)));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldReturn403ForNonAdminToggleActive() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/toggle-active"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateUserRole() throws Exception {
        Map<String, String> request = Map.of("role", "ADMIN");

        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ADMIN")));

        // Restore to USER
        Map<String, String> restore = Map.of("role", "USER");
        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(restore)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldReturn403ForNonAdminUpdateRole() throws Exception {
        Map<String, String> request = Map.of("role", "ADMIN");

        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
