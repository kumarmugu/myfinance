package com.myfinance.controller;

import com.myfinance.model.BankSavings;
import com.myfinance.model.CurrencyRate;
import com.myfinance.model.NetWorthConfig;
import com.myfinance.model.Property;
import com.myfinance.model.enums.Currency;
import com.myfinance.repository.BankSavingsRepository;
import com.myfinance.repository.CurrencyRateRepository;
import com.myfinance.repository.NetWorthConfigRepository;
import com.myfinance.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that standalone asset modules (Bank Savings, Property) are FX-converted
 * to the base currency (SGD) and included in the dashboard net worth, respecting the
 * per-module Net Worth Config toggle and each record's includeInNetWorth flag.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NetWorthAggregationTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BankSavingsRepository bankSavingsRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private CurrencyRateRepository currencyRateRepository;
    @Autowired private NetWorthConfigRepository netWorthConfigRepository;

    @BeforeEach
    void setup() {
        bankSavingsRepository.deleteAll();
        propertyRepository.deleteAll();
        currencyRateRepository.deleteAll();
        netWorthConfigRepository.deleteAll();
    }

    private void seedUsdRate(String value) {
        // USD -> SGD rate maintained by the user
        currencyRateRepository.save(CurrencyRate.builder()
                .fromCurrency("USD").toCurrency("SGD")
                .rate(new BigDecimal(value)).effectiveDate(LocalDate.of(2026, 1, 1))
                .userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void bankSavingsIncludedInNetWorth() throws Exception {
        // 1000 SGD saving, no holdings -> net worth 1000
        bankSavingsRepository.save(BankSavings.builder()
                .accountName("DBS").bankName("DBS").balance(new BigDecimal("1000"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true)
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("SGD")))
                .andExpect(jsonPath("$.totalNetWorth", closeTo(1000.0, 0.01)))
                .andExpect(jsonPath("$.allocationByType.BANK_SAVINGS", closeTo(1000.0, 0.01)));
    }

    @Test
    @WithMockUser
    void foreignCurrencySavingIsConverted() throws Exception {
        seedUsdRate("1.35");
        // 100 USD -> 135 SGD
        bankSavingsRepository.save(BankSavings.builder()
                .accountName("US Acct").bankName("Chase").balance(new BigDecimal("100"))
                .currency(Currency.USD).country("Singapore").includeInNetWorth(true)
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNetWorth", closeTo(135.0, 0.01)))
                .andExpect(jsonPath("$.displayRates.SGD", is(1)));
    }

    @Test
    @WithMockUser
    void propertyContributesEquityValueMinusLoan() throws Exception {
        // value 1,000,000 - loan 400,000 = 600,000 equity (SGD)
        propertyRepository.save(Property.builder()
                .propertyName("Condo").propertyType("CONDO").currency("SGD")
                .currentValue(new BigDecimal("1000000")).outstandingLoan(new BigDecimal("400000"))
                .status("OWNED").includeInNetWorth(true)
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationByType.PROPERTY", closeTo(600000.0, 0.01)))
                .andExpect(jsonPath("$.totalNetWorth", closeTo(600000.0, 0.01)));
    }

    @Test
    @WithMockUser
    void perRecordFlagExcludesFromNetWorth() throws Exception {
        bankSavingsRepository.save(BankSavings.builder()
                .accountName("Excluded").bankName("DBS").balance(new BigDecimal("5000"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(false)
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNetWorth", is(0)));
    }

    @Test
    @WithMockUser
    void moduleToggleOffExcludesEntireModule() throws Exception {
        bankSavingsRepository.save(BankSavings.builder()
                .accountName("DBS").bankName("DBS").balance(new BigDecimal("1000"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true)
                .userId(testUser.getId()).build());

        // Auto-create the full config set for this user (as the config screen does)...
        mockMvc.perform(get("/api/net-worth-config")).andExpect(status().isOk());
        // ...then disable just the BANK_SAVINGS module.
        NetWorthConfig bankCfg = netWorthConfigRepository
                .findByUserIdAndIncludeInNetWorthTrue(testUser.getId()).stream()
                .filter(c -> "BANK_SAVINGS".equals(c.getAssetType())).findFirst().orElseThrow();
        bankCfg.setIncludeInNetWorth(false);
        netWorthConfigRepository.save(bankCfg);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNetWorth", is(0)))
                .andExpect(jsonPath("$.allocationByType.BANK_SAVINGS").doesNotExist());
    }

    @Test
    @WithMockUser
    void configEndpointAutoCreatesModuleKeys() throws Exception {
        mockMvc.perform(get("/api/net-worth-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.assetType == 'BANK_SAVINGS')]").exists())
                .andExpect(jsonPath("$[?(@.assetType == 'PROPERTY')]").exists())
                .andExpect(jsonPath("$[?(@.assetType == 'PRECIOUS_METAL')]").exists())
                .andExpect(jsonPath("$[?(@.assetType == 'GENERIC_FD')]").exists());
    }
}
