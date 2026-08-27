package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.GenericFixedDeposit;
import com.myfinance.repository.GenericFixedDepositRepository;
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
class GenericFixedDepositControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GenericFixedDepositRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    private GenericFixedDeposit.GenericFixedDepositBuilder baseFd() {
        return GenericFixedDeposit.builder()
                .bankName("DBS")
                .accountNumber("FD-12345")
                .principalAmount(new BigDecimal("100000"))
                .interestRate(new BigDecimal("3.5"))
                .startDate(LocalDate.of(2024, 1, 1))
                .maturityDate(LocalDate.of(2025, 1, 1)) // 366 days (2024 leap year)
                .tenure("12 months")
                .currency("SGD")
                .status("ACTIVE")
                .includeInNetWorth(true);
    }

    @Test
    @WithMockUser
    void shouldCreateFdWithAutoCalculatedInterest() throws Exception {
        // 100000 * 3.5 * 365 / 36500 = 3500. Use a 365-day span.
        GenericFixedDeposit fd = baseFd()
                .startDate(LocalDate.of(2024, 1, 1))
                .maturityDate(LocalDate.of(2024, 12, 31)) // 365 days
                .build();

        mockMvc.perform(post("/api/generic-fd")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankName", is("DBS")))
                .andExpect(jsonPath("$.expectedInterest", closeTo(3500.0, 0.01)));
    }

    @Test
    @WithMockUser
    void shouldCreateFdWithExplicitInterest() throws Exception {
        GenericFixedDeposit fd = baseFd()
                .expectedInterest(new BigDecimal("9999.99"))
                .build();

        mockMvc.perform(post("/api/generic-fd")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedInterest", closeTo(9999.99, 0.01)));
    }

    @Test
    @WithMockUser
    void shouldListFds() throws Exception {
        repository.save(baseFd().bankName("A").userId(testUser.getId()).build());
        repository.save(baseFd().bankName("B").userId(testUser.getId()).build());

        mockMvc.perform(get("/api/generic-fd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldUpdateAndRecalculateInterest() throws Exception {
        GenericFixedDeposit saved = repository.save(baseFd()
                .expectedInterest(new BigDecimal("1000"))
                .userId(testUser.getId()).build());

        // Update to 200000 principal, 4.0 rate, 365-day span => 200000*4*365/36500 = 8000
        GenericFixedDeposit update = baseFd()
                .bankName("OCBC")
                .principalAmount(new BigDecimal("200000"))
                .interestRate(new BigDecimal("4.0"))
                .startDate(LocalDate.of(2024, 1, 1))
                .maturityDate(LocalDate.of(2024, 12, 31)) // 365 days
                .build();

        mockMvc.perform(put("/api/generic-fd/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName", is("OCBC")))
                .andExpect(jsonPath("$.expectedInterest", closeTo(8000.0, 0.01)));
    }

    @Test
    @WithMockUser
    void shouldDeleteFd() throws Exception {
        GenericFixedDeposit saved = repository.save(baseFd().userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/generic-fd/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/generic-fd"))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
