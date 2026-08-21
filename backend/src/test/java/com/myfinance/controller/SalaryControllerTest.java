package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.SalaryRecord;
import com.myfinance.repository.SalaryRecordRepository;
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
class SalaryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SalaryRecordRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    @Test
    @WithMockUser
    void shouldCreateSalaryRecord() throws Exception {
        SalaryRecord record = SalaryRecord.builder()
                .year(2026).month(1).company("BCS")
                .amount(new BigDecimal("14442")).basic(new BigDecimal("14400"))
                .mobile(new BigDecimal("60")).country("Singapore").build();

        mockMvc.perform(post("/api/salary")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(record)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company", is("BCS")))
                .andExpect(jsonPath("$.year", is(2026)));
    }

    @Test
    @WithMockUser
    void shouldCreateBonusRecord() throws Exception {
        SalaryRecord bonus = SalaryRecord.builder()
                .year(2026).month(3).company("BCS")
                .amount(new BigDecimal("56000")).isBonus(true)
                .bonusMonths(new BigDecimal("4.04")).country("Singapore").build();

        mockMvc.perform(post("/api/salary")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bonus)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isBonus", is(true)))
                .andExpect(jsonPath("$.bonusMonths", is(4.04)));
    }

    @Test
    @WithMockUser
    void shouldFilterByYear() throws Exception {
        repository.save(SalaryRecord.builder().year(2025).month(1).company("BCS").amount(new BigDecimal("13000")).build());
        repository.save(SalaryRecord.builder().year(2026).month(1).company("BCS").amount(new BigDecimal("14000")).build());

        mockMvc.perform(get("/api/salary?year=2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].year", is(2026)));
    }

    @Test
    @WithMockUser
    void shouldReturnSummary() throws Exception {
        repository.save(SalaryRecord.builder().year(2025).month(1).company("BCS").amount(new BigDecimal("13000")).build());
        repository.save(SalaryRecord.builder().year(2025).month(2).company("BCS").amount(new BigDecimal("13000")).build());

        mockMvc.perform(get("/api/salary/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal", is(26000.0)))
                .andExpect(jsonPath("$.years", is(1)));
    }
}
