package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.RetirementFundEntry;
import com.myfinance.repository.RetirementFundEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RetirementFundControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RetirementFundEntryRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    @Test
    @WithMockUser
    void shouldCreateCPFContribution() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("CPF").entryType("CONTRIBUTION")
                .amount(new BigDecimal("2500")).entryDate(LocalDate.of(2026, 1, 15))
                .account("OA").employer("BCS").build();

        mockMvc.perform(post("/api/retirement-fund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fundType", is("CPF")))
                .andExpect(jsonPath("$.entryType", is("CONTRIBUTION")))
                .andExpect(jsonPath("$.year", is(2026)));
    }

    @Test
    @WithMockUser
    void shouldFilterByFundType() throws Exception {
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2000")).entryDate(LocalDate.now()).build());
        repository.save(RetirementFundEntry.builder().fundType("SRS").entryType("CONTRIBUTION").amount(new BigDecimal("15300")).entryDate(LocalDate.now()).build());

        mockMvc.perform(get("/api/retirement-fund?fundType=CPF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fundType", is("CPF")));
    }

    @Test
    @WithMockUser
    void shouldCreateWithdrawal() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("CPF").entryType("WITHDRAWAL")
                .amount(new BigDecimal("50000")).entryDate(LocalDate.of(2026, 5, 1))
                .account("OA").build();

        mockMvc.perform(post("/api/retirement-fund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryType", is("WITHDRAWAL")));
    }
}
