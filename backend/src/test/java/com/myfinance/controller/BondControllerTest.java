package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Bond;
import com.myfinance.repository.BondRepository;
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
class BondControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BondRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    private Bond.BondBuilder govBond() {
        return Bond.builder()
                .name("Singapore Savings Bond Oct 2025")
                .issuer("MAS")
                .bondType("GOVERNMENT")
                .currency("SGD")
                .faceValue(new BigDecimal("10000"))
                .purchasePrice(new BigDecimal("10000"))
                .currentValue(new BigDecimal("10200"))
                .couponRate(new BigDecimal("3.5000"))
                .couponFrequency("SEMI_ANNUAL")
                .purchaseDate(LocalDate.of(2025, 10, 1))
                .maturityDate(LocalDate.of(2035, 10, 1))
                .status("HELD")
                .includeInNetWorth(true);
    }

    private Bond.BondBuilder corpBond() {
        return Bond.builder()
                .name("Temasek 5Y")
                .issuer("Temasek")
                .bondType("CORPORATE")
                .currency("SGD")
                .faceValue(new BigDecimal("5000"))
                .purchasePrice(new BigDecimal("5000"))
                .currentValue(new BigDecimal("5100"))
                .couponRate(new BigDecimal("4.0000"))
                .couponFrequency("ANNUAL")
                .purchaseDate(LocalDate.of(2024, 1, 1))
                .maturityDate(LocalDate.of(2029, 1, 1))
                .status("HELD")
                .includeInNetWorth(true);
    }

    @Test
    @WithMockUser
    void shouldCreateBond() throws Exception {
        mockMvc.perform(post("/api/bonds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(govBond().build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Singapore Savings Bond Oct 2025")))
                .andExpect(jsonPath("$.bondType", is("GOVERNMENT")))
                .andExpect(jsonPath("$.status", is("HELD")));
    }

    @Test
    @WithMockUser
    void shouldListAllOrderedByMaturity() throws Exception {
        repository.save(govBond().userId(testUser.getId()).build());
        repository.save(corpBond().userId(testUser.getId()).build());

        mockMvc.perform(get("/api/bonds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // corp bond matures 2029 (earlier) so should be first (asc)
                .andExpect(jsonPath("$[0].name", is("Temasek 5Y")));
    }

    @Test
    @WithMockUser
    void shouldGetById() throws Exception {
        Bond saved = repository.save(govBond().userId(testUser.getId()).build());
        mockMvc.perform(get("/api/bonds/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer", is("MAS")));
    }

    @Test
    @WithMockUser
    void shouldUpdate() throws Exception {
        Bond saved = repository.save(govBond().userId(testUser.getId()).build());
        Bond update = govBond().currentValue(new BigDecimal("10500")).status("MATURED").build();

        mockMvc.perform(put("/api/bonds/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue", is(10500)))
                .andExpect(jsonPath("$.status", is("MATURED")));
    }

    @Test
    @WithMockUser
    void shouldDelete() throws Exception {
        Bond saved = repository.save(govBond().userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/bonds/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bonds"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldGetSummaryForHeldOnly() throws Exception {
        // HELD gov: invested 10000, current 10200
        repository.save(govBond().userId(testUser.getId()).build());
        // HELD corp: invested 5000, current 5100
        repository.save(corpBond().userId(testUser.getId()).build());
        // SOLD bond should be excluded
        repository.save(govBond()
                .name("Sold Bond")
                .faceValue(new BigDecimal("99999"))
                .purchasePrice(new BigDecimal("99999"))
                .currentValue(new BigDecimal("99999"))
                .status("SOLD")
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/bonds/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBonds", is(2)))
                .andExpect(jsonPath("$.totalInvested", is(15000.00)))
                .andExpect(jsonPath("$.totalCurrentValue", is(15300.00)))
                .andExpect(jsonPath("$.gainLoss", is(300.00)))
                .andExpect(jsonPath("$.baseCurrency", is("SGD")));
    }
}
