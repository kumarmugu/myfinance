package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Asset;
import com.myfinance.model.enums.AssetType;
import com.myfinance.model.enums.Currency;
import com.myfinance.repository.AssetRepository;
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
class AssetControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AssetRepository assetRepository;

    @BeforeEach
    void setup() {
        assetRepository.deleteAll();
    }

    @Test
    @WithMockUser
    void shouldCreateAsset() throws Exception {
        Asset asset = Asset.builder().name("Vanguard S&P 500").symbol("VOO")
                .assetType(AssetType.INDEX_FUND).currency(Currency.USD).build();

        mockMvc.perform(post("/api/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(asset)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", is("VOO")))
                .andExpect(jsonPath("$.assetType", is("INDEX_FUND")));
    }

    @Test
    @WithMockUser
    void shouldGetAssetTypes() throws Exception {
        mockMvc.perform(get("/api/assets/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("INDEX_FUND")))
                .andExpect(jsonPath("$", hasItem("GOLD")))
                .andExpect(jsonPath("$", hasItem("CRYPTO")))
                .andExpect(jsonPath("$", hasItem("REIT")));
    }

    @Test
    @WithMockUser
    void shouldFilterByType() throws Exception {
        assetRepository.save(Asset.builder().name("VOO").symbol("VOO").assetType(AssetType.INDEX_FUND).currency(Currency.USD).build());
        assetRepository.save(Asset.builder().name("BTC").symbol("BTC").assetType(AssetType.CRYPTO).currency(Currency.USD).build());

        mockMvc.perform(get("/api/assets/type/INDEX_FUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("VOO")));
    }

    @Test
    @WithMockUser
    void shouldToggleNetWorth() throws Exception {
        Asset asset = assetRepository.save(Asset.builder().name("BTC").symbol("BTC-TEST")
                .assetType(AssetType.CRYPTO).currency(Currency.USD).includeInNetWorth(true).build());

        mockMvc.perform(patch("/api/assets/" + asset.getId() + "/net-worth?include=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeInNetWorth", is(false)));
    }

    @Test
    @WithMockUser
    void shouldSearchAssets() throws Exception {
        assetRepository.save(Asset.builder().name("Apple Inc").symbol("AAPL").assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());
        assetRepository.save(Asset.builder().name("Tesla").symbol("TSLA").assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).build());

        mockMvc.perform(get("/api/assets/search?query=apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")));
    }

    @Test
    @WithMockUser
    void shouldUpdateAsset() throws Exception {
        Asset a = assetRepository.save(Asset.builder().name("Old").symbol("OLD-T").assetType(AssetType.CRYPTO).currency(Currency.USD).userId(testUser.getId()).build());
        Asset update = Asset.builder().name("New Name").symbol("OLD-T").assetType(AssetType.CRYPTO).currency(Currency.USD).build();
        mockMvc.perform(put("/api/assets/" + a.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", is("New Name")));
    }

    @Test
    @WithMockUser
    void shouldDeleteAssetWithNoReferences() throws Exception {
        Asset a = assetRepository.save(Asset.builder().name("Del").symbol("DEL-T").assetType(AssetType.OTHER).currency(Currency.USD).userId(testUser.getId()).build());
        mockMvc.perform(delete("/api/assets/" + a.getId())).andExpect(status().isNoContent());
    }
}
