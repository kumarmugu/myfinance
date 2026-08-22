package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.CurrencyRate;
import com.myfinance.repository.CurrencyRateRepository;
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
class CurrencyRateControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CurrencyRateRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    @Test
    @WithMockUser
    void shouldCreateCurrencyRate() throws Exception {
        CurrencyRate rate = CurrencyRate.builder()
                .fromCurrency("USD").toCurrency("SGD")
                .rate(new BigDecimal("1.3500"))
                .effectiveDate(LocalDate.now()).build();

        mockMvc.perform(post("/api/currency-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCurrency", is("USD")))
                .andExpect(jsonPath("$.toCurrency", is("SGD")));
    }

    @Test
    @WithMockUser
    void shouldGetAvailableCurrencies() throws Exception {
        mockMvc.perform(get("/api/currency-rates/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("SGD")))
                .andExpect(jsonPath("$", hasItem("USD")))
                .andExpect(jsonPath("$", hasItem("INR")));
    }

    @Test
    @WithMockUser
    void shouldUpdateRate() throws Exception {
        CurrencyRate saved = repository.save(CurrencyRate.builder().fromCurrency("EUR").toCurrency("SGD").rate(new BigDecimal("1.4500")).effectiveDate(LocalDate.now()).build());

        CurrencyRate update = CurrencyRate.builder().rate(new BigDecimal("1.4800")).effectiveDate(LocalDate.now()).build();
        mockMvc.perform(put("/api/currency-rates/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate", is(1.48)));
    }

    @Test
    @WithMockUser
    void shouldDeleteRate() throws Exception {
        CurrencyRate saved = repository.save(CurrencyRate.builder().fromCurrency("LKR").toCurrency("SGD").rate(new BigDecimal("0.004")).effectiveDate(LocalDate.now()).build());

        mockMvc.perform(delete("/api/currency-rates/" + saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void shouldSupportCustomCurrencyCodes() throws Exception {
        CurrencyRate rate = CurrencyRate.builder()
                .fromCurrency("BTC").toCurrency("USD")
                .rate(new BigDecimal("65000"))
                .effectiveDate(LocalDate.now()).build();

        mockMvc.perform(post("/api/currency-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCurrency", is("BTC")));
    }
}
