package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.PreciousMetal;
import com.myfinance.repository.PreciousMetalRepository;
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
class PreciousMetalControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PreciousMetalRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    private PreciousMetal.PreciousMetalBuilder gold() {
        return PreciousMetal.builder()
                .metalType("GOLD")
                .form("COIN")
                .description("1oz Gold Coin")
                .weight(new BigDecimal("31.1035"))
                .weightUnit("g")
                .purity("999")
                .purchasePrice(new BigDecimal("2500"))
                .currentPrice(new BigDecimal("3000"))
                .currency("SGD")
                .purchaseDate(LocalDate.of(2023, 5, 1))
                .purchasedFrom("BullionStar")
                .storageLocation("Home Safe")
                .includeInNetWorth(true)
                .status("HELD");
    }

    private PreciousMetal.PreciousMetalBuilder silver() {
        return PreciousMetal.builder()
                .metalType("SILVER")
                .form("BAR")
                .description("1kg Silver Bar")
                .weight(new BigDecimal("1000"))
                .weightUnit("g")
                .purity("999")
                .purchasePrice(new BigDecimal("1000"))
                .currentPrice(new BigDecimal("1100"))
                .currency("SGD")
                .purchaseDate(LocalDate.of(2023, 6, 1))
                .includeInNetWorth(true)
                .status("HELD");
    }

    @Test
    @WithMockUser
    void shouldCreateGold() throws Exception {
        mockMvc.perform(post("/api/precious-metals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(gold().build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metalType", is("GOLD")))
                .andExpect(jsonPath("$.form", is("COIN")));
    }

    @Test
    @WithMockUser
    void shouldCreateSilver() throws Exception {
        mockMvc.perform(post("/api/precious-metals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(silver().build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metalType", is("SILVER")))
                .andExpect(jsonPath("$.form", is("BAR")));
    }

    @Test
    @WithMockUser
    void shouldListAll() throws Exception {
        repository.save(gold().userId(testUser.getId()).build());
        repository.save(silver().userId(testUser.getId()).build());

        mockMvc.perform(get("/api/precious-metals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldFilterByMetalType() throws Exception {
        repository.save(gold().userId(testUser.getId()).build());
        repository.save(silver().userId(testUser.getId()).build());

        mockMvc.perform(get("/api/precious-metals").param("metalType", "GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].metalType", is("GOLD")));
    }

    @Test
    @WithMockUser
    void shouldUpdate() throws Exception {
        PreciousMetal saved = repository.save(gold().userId(testUser.getId()).build());

        PreciousMetal update = gold()
                .description("Updated Coin")
                .currentPrice(new BigDecimal("3200"))
                .build();

        mockMvc.perform(put("/api/precious-metals/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Updated Coin")))
                .andExpect(jsonPath("$.currentPrice", is(3200)));
    }

    @Test
    @WithMockUser
    void shouldDelete() throws Exception {
        PreciousMetal saved = repository.save(gold().userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/precious-metals/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/precious-metals"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldGetSummaryForHeldItemsOnly() throws Exception {
        // HELD gold: weight 100g, purchase 2000, current 2500
        repository.save(gold()
                .weight(new BigDecimal("100"))
                .purchasePrice(new BigDecimal("2000"))
                .currentPrice(new BigDecimal("2500"))
                .status("HELD")
                .userId(testUser.getId()).build());
        // HELD silver: weight 500g, purchase 500, current 600
        repository.save(silver()
                .weight(new BigDecimal("500"))
                .purchasePrice(new BigDecimal("500"))
                .currentPrice(new BigDecimal("600"))
                .status("HELD")
                .userId(testUser.getId()).build());
        // SOLD gold should be excluded
        repository.save(gold()
                .weight(new BigDecimal("999"))
                .purchasePrice(new BigDecimal("9999"))
                .currentPrice(new BigDecimal("9999"))
                .status("SOLD")
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/precious-metals/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", is(2)))
                .andExpect(jsonPath("$.totalGoldGrams", is(100.0000)))
                .andExpect(jsonPath("$.totalSilverGrams", is(500.0000)))
                .andExpect(jsonPath("$.totalPurchaseValue", is(2500.00)))
                .andExpect(jsonPath("$.totalCurrentValue", is(3100.00)))
                .andExpect(jsonPath("$.gainLoss", is(600.00)));
    }
}
