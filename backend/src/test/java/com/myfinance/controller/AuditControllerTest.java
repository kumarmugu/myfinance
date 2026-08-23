package com.myfinance.controller;

import com.myfinance.model.AppUser;
import com.myfinance.model.AuditLog;
import com.myfinance.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditLogRepository auditLogRepository;

    private AppUser adminUser;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();

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

        // Seed some audit logs
        auditLogRepository.save(AuditLog.builder()
                .userId(testUser.getId()).username("user")
                .action("CREATE").entity("Account").entityId(1L)
                .details("Created account").timestamp(LocalDateTime.of(2024, 3, 15, 10, 0))
                .build());
        auditLogRepository.save(AuditLog.builder()
                .userId(testUser.getId()).username("user")
                .action("UPDATE").entity("Asset").entityId(2L)
                .details("Updated asset").timestamp(LocalDateTime.of(2024, 3, 16, 14, 30))
                .build());
        auditLogRepository.save(AuditLog.builder()
                .userId(adminUser.getId()).username("admin")
                .action("DELETE").entity("Account").entityId(3L)
                .details("Deleted account").timestamp(LocalDateTime.of(2024, 4, 1, 9, 0))
                .build());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnAuditLogsForAdmin() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldReturn403ForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldFilterByAction() throws Exception {
        mockMvc.perform(get("/api/audit").param("action", "CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action", is("CREATE")));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldFilterByEntity() throws Exception {
        mockMvc.perform(get("/api/audit").param("entity", "Account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].entity", everyItem(is("Account"))));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldFilterByDateRange() throws Exception {
        mockMvc.perform(get("/api/audit")
                .param("from", "2024-03-15")
                .param("to", "2024-03-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnEmptyWhenNoMatchingFilters() throws Exception {
        mockMvc.perform(get("/api/audit").param("action", "NONEXISTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldSupportPagination() throws Exception {
        mockMvc.perform(get("/api/audit").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)));
    }
}
