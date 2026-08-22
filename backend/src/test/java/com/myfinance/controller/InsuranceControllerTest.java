package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.InsurancePolicy;
import com.myfinance.model.enums.Currency;
import com.myfinance.repository.InsurancePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InsuranceControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InsurancePolicyRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    @Test
    @WithMockUser
    void shouldCreatePolicy() throws Exception {
        InsurancePolicy policy = InsurancePolicy.builder().policyName("AIA Life").provider("AIA").policyType("TERM_LIFE").annualPremium(new BigDecimal("2000")).currency(Currency.SGD).build();
        mockMvc.perform(post("/api/insurance").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(policy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyName", is("AIA Life")));
    }

    @Test
    @WithMockUser
    void shouldListPolicies() throws Exception {
        repository.save(InsurancePolicy.builder().policyName("Test").policyType("WHOLE_LIFE").annualPremium(new BigDecimal("1000")).currency(Currency.SGD).userId(testUser.getId()).build());
        mockMvc.perform(get("/api/insurance")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser
    void shouldDeletePolicy() throws Exception {
        InsurancePolicy saved = repository.save(InsurancePolicy.builder().policyName("Del").policyType("TERM_LIFE").annualPremium(new BigDecimal("500")).currency(Currency.SGD).userId(testUser.getId()).build());
        mockMvc.perform(delete("/api/insurance/" + saved.getId())).andExpect(status().isNoContent());
    }
}
