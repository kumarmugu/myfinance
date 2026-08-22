package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.TaxRecord;
import com.myfinance.repository.TaxRecordRepository;
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
class TaxControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TaxRecordRepository taxRecordRepository;

    @BeforeEach
    void setup() {
        taxRecordRepository.deleteAll();
    }

    @Test
    @WithMockUser
    void shouldCreateTaxRecord() throws Exception {
        TaxRecord record = TaxRecord.builder()
                .assessmentYear(2024).employment(new BigDecimal("220000"))
                .donations(new BigDecimal("570")).reliefs(new BigDecimal("1000"))
                .srsDeduction(BigDecimal.ZERO).chargeableIncome(new BigDecimal("218220"))
                .tax(new BigDecimal("24611.80")).taxRebate(new BigDecimal("200"))
                .taxPayable(new BigDecimal("24411.80")).country("Singapore").build();

        mockMvc.perform(post("/api/tax")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(record)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assessmentYear", is(2024)))
                .andExpect(jsonPath("$.country", is("Singapore")));
    }

    @Test
    @WithMockUser
    void shouldGetTaxSummary() throws Exception {
        taxRecordRepository.save(TaxRecord.builder().assessmentYear(2023).employment(new BigDecimal("197000")).taxPayable(new BigDecimal("20000")).userId(testUser.getId()).build());
        taxRecordRepository.save(TaxRecord.builder().assessmentYear(2024).employment(new BigDecimal("220000")).taxPayable(new BigDecimal("24000")).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/tax/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years", is(2)))
                .andExpect(jsonPath("$.totalTaxPaid", greaterThan(40000.0)));
    }

    @Test
    @WithMockUser
    void shouldUpdateTaxRecord() throws Exception {
        TaxRecord saved = taxRecordRepository.save(TaxRecord.builder().assessmentYear(2024).employment(new BigDecimal("220000")).taxPayable(new BigDecimal("24000")).build());

        saved.setTaxPayable(new BigDecimal("25000"));
        mockMvc.perform(put("/api/tax/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxPayable", is(25000)));
    }

    @Test
    @WithMockUser
    void shouldDeleteTaxRecord() throws Exception {
        TaxRecord saved = taxRecordRepository.save(TaxRecord.builder().assessmentYear(2024).employment(new BigDecimal("100000")).taxPayable(new BigDecimal("5000")).build());

        mockMvc.perform(delete("/api/tax/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tax"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldGetTaxById() throws Exception {
        TaxRecord saved = taxRecordRepository.save(TaxRecord.builder().assessmentYear(2025).employment(new BigDecimal("250000")).taxPayable(new BigDecimal("30000")).userId(testUser.getId()).build());
        mockMvc.perform(get("/api/tax/" + saved.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.assessmentYear", is(2025)));
    }
}
