package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.AppUser;
import com.myfinance.model.Owner;
import com.myfinance.model.TaxRecord;
import com.myfinance.model.enums.OwnerRelationship;
import com.myfinance.repository.AppUserRepository;
import com.myfinance.repository.OwnerRepository;
import com.myfinance.repository.TaxRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CRITICAL: Tests that User A cannot see User B's data.
 * This is the most important security test in the application.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MultiTenantIsolationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private TaxRecordRepository taxRecordRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setup() {
        taxRecordRepository.deleteAll();
        ownerRepository.deleteAll();

        // Create two separate users
        if (appUserRepository.findByUsername("userA").isEmpty()) {
            userA = appUserRepository.save(AppUser.builder()
                    .username("userA").email("a@test.com")
                    .password(passwordEncoder.encode("pass"))
                    .displayName("User A").role("USER").build());
        } else {
            userA = appUserRepository.findByUsername("userA").get();
        }

        if (appUserRepository.findByUsername("userB").isEmpty()) {
            userB = appUserRepository.save(AppUser.builder()
                    .username("userB").email("b@test.com")
                    .password(passwordEncoder.encode("pass"))
                    .displayName("User B").role("USER").build());
        } else {
            userB = appUserRepository.findByUsername("userB").get();
        }

        // User A's data
        ownerRepository.save(Owner.builder().name("A-Owner").relationship(OwnerRelationship.SELF).userId(userA.getId()).build());
        taxRecordRepository.save(TaxRecord.builder().assessmentYear(2024).employment(new BigDecimal("100000")).taxPayable(new BigDecimal("5000")).userId(userA.getId()).build());

        // User B's data
        ownerRepository.save(Owner.builder().name("B-Owner").relationship(OwnerRelationship.SELF).userId(userB.getId()).build());
        taxRecordRepository.save(TaxRecord.builder().assessmentYear(2024).employment(new BigDecimal("200000")).taxPayable(new BigDecimal("15000")).userId(userB.getId()).build());
    }

    @Test
    @WithMockUser(username = "userA")
    void userA_shouldOnlySeeOwnOwners() throws Exception {
        mockMvc.perform(get("/api/owners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("A-Owner")));
    }

    @Test
    @WithMockUser(username = "userB")
    void userB_shouldOnlySeeOwnOwners() throws Exception {
        mockMvc.perform(get("/api/owners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("B-Owner")));
    }

    @Test
    @WithMockUser(username = "userA")
    void userA_shouldNotSeeBsTaxRecords() throws Exception {
        mockMvc.perform(get("/api/tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employment", is(100000.0)));
    }

    @Test
    @WithMockUser(username = "userB")
    void userB_shouldNotSeeAsTaxRecords() throws Exception {
        mockMvc.perform(get("/api/tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employment", is(200000.0)));
    }

    @Test
    @WithMockUser(username = "userA")
    void userA_taxSummary_shouldOnlyIncludeOwnData() throws Exception {
        mockMvc.perform(get("/api/tax/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTaxPaid", is(5000.0)))
                .andExpect(jsonPath("$.years", is(1)));
    }
}
