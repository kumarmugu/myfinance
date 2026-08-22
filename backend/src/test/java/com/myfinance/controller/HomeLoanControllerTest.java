package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.HomeLoan;
import com.myfinance.model.enums.Currency;
import com.myfinance.repository.HomeLoanRepository;
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
class HomeLoanControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HomeLoanRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    @Test
    @WithMockUser
    void shouldCreateHomeLoan() throws Exception {
        HomeLoan loan = HomeLoan.builder()
                .propertyName("Condo Woodlands")
                .propertyValue(new BigDecimal("800000"))
                .loanAmount(new BigDecimal("600000"))
                .interestRate(new BigDecimal("3.5"))
                .loanType("FIXED")
                .tenureMonths(300)
                .monthlyEmi(new BigDecimal("3000"))
                .outstandingBalance(new BigDecimal("580000"))
                .startDate(LocalDate.of(2024, 6, 1))
                .bank("DBS")
                .currency(Currency.SGD)
                .build();

        mockMvc.perform(post("/api/home-loans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loan)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propertyName", is("Condo Woodlands")))
                .andExpect(jsonPath("$.loanType", is("FIXED")));
    }

    @Test
    @WithMockUser
    void shouldListActiveLoans() throws Exception {
        repository.save(HomeLoan.builder().propertyName("House A").propertyValue(new BigDecimal("500000")).loanAmount(new BigDecimal("400000")).interestRate(new BigDecimal("3")).tenureMonths(240).currency(Currency.SGD).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/home-loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser
    void shouldSoftDelete() throws Exception {
        HomeLoan loan = repository.save(HomeLoan.builder().propertyName("Old").propertyValue(new BigDecimal("300000")).loanAmount(new BigDecimal("200000")).interestRate(new BigDecimal("4")).tenureMonths(180).currency(Currency.SGD).build());

        mockMvc.perform(delete("/api/home-loans/" + loan.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/home-loans"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldUpdateHomeLoan() throws Exception {
        HomeLoan loan = repository.save(HomeLoan.builder().propertyName("Old").propertyValue(new BigDecimal("500000")).loanAmount(new BigDecimal("400000")).interestRate(new BigDecimal("3")).tenureMonths(300).currency(Currency.SGD).userId(testUser.getId()).build());
        HomeLoan update = HomeLoan.builder().propertyName("Updated").propertyValue(new BigDecimal("600000")).loanAmount(new BigDecimal("400000")).interestRate(new BigDecimal("3.5")).tenureMonths(300).build();
        mockMvc.perform(put("/api/home-loans/" + loan.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.propertyName", is("Updated")));
    }
}
