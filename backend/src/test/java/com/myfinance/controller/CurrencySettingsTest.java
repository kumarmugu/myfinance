package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.AppUser;
import com.myfinance.model.BankSavings;
import com.myfinance.model.CurrencyRate;
import com.myfinance.model.enums.Currency;
import com.myfinance.repository.AppUserRepository;
import com.myfinance.repository.BankSavingsRepository;
import com.myfinance.repository.CurrencyRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies per-user configurable base currency + display currencies, and that the
 * configuration affects consolidation/display without ever mutating original values.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CurrencySettingsTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BankSavingsRepository bankSavingsRepository;
    @Autowired private CurrencyRateRepository currencyRateRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser admin;

    @BeforeEach
    void setup() {
        bankSavingsRepository.deleteAll();
        currencyRateRepository.deleteAll();
        admin = appUserRepository.findByUsername("admin").orElseGet(() ->
                appUserRepository.save(AppUser.builder()
                        .username("admin").email("admin@test.com")
                        .password(passwordEncoder.encode("admin123"))
                        .displayName("Admin").role("ADMIN").build()));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanSetCurrencySettings() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/currency-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "baseCurrency", "usd",
                        "displayCurrencies", "usd, sgd, eur"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("USD")))
                .andExpect(jsonPath("$.displayCurrencies", is("USD,SGD,EUR")));

        AppUser reloaded = appUserRepository.findById(testUser.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("USD", reloaded.getBaseCurrency());
        org.junit.jupiter.api.Assertions.assertEquals("USD,SGD,EUR", reloaded.getDisplayCurrencies());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void nonAdminCannotSetCurrencySettings() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + testUser.getId() + "/currency-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseCurrency\":\"USD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user")
    void netWorthUsesConfiguredBaseCurrency() throws Exception {
        // Configure the acting user's base currency to USD directly.
        testUser.setBaseCurrency("USD");
        appUserRepository.save(testUser);

        // Rate: SGD -> USD = 0.75 (so an SGD balance converts into USD for the base total).
        currencyRateRepository.save(CurrencyRate.builder()
                .fromCurrency("SGD").toCurrency("USD").rate(new BigDecimal("0.75"))
                .effectiveDate(LocalDate.of(2026, 1, 1)).userId(testUser.getId()).build());

        // 1000 SGD savings -> 750 USD in the base currency.
        bankSavingsRepository.save(BankSavings.builder()
                .accountName("SGD Acct").bankName("DBS").balance(new BigDecimal("1000"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true)
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("USD")))
                .andExpect(jsonPath("$.totalNetWorth", closeTo(750.0, 0.01)));

        // The stored original value is untouched: still 1000 SGD.
        BankSavings stored = bankSavingsRepository.findByUserIdOrderByAccountNameAsc(testUser.getId()).get(0);
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("1000").compareTo(stored.getBalance()));
        org.junit.jupiter.api.Assertions.assertEquals(Currency.SGD, stored.getCurrency());
    }

    @Test
    @WithMockUser(username = "user")
    void changingDisplayCurrencyNeverMutatesStoredValue() throws Exception {
        bankSavingsRepository.save(BankSavings.builder()
                .accountName("LKR Acct").bankName("BOC").balance(new BigDecimal("1000000"))
                .currency(Currency.LKR).country("Sri Lanka").includeInNetWorth(true)
                .userId(testUser.getId()).build());

        // Reading the summary (a display/consolidation action) must not alter the record.
        mockMvc.perform(get("/api/bank-savings/summary")).andExpect(status().isOk());
        mockMvc.perform(get("/api/bank-savings")).andExpect(status().isOk());

        BankSavings stored = bankSavingsRepository.findByUserIdOrderByAccountNameAsc(testUser.getId()).get(0);
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("1000000").compareTo(stored.getBalance()));
        org.junit.jupiter.api.Assertions.assertEquals(Currency.LKR, stored.getCurrency());
    }
}
