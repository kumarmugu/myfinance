package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.NetWorthConfig;
import com.myfinance.repository.NetWorthConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NetWorthConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NetWorthConfigRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    @Test
    @WithMockUser
    void shouldAutoCreateConfigsForAllAssetTypes() throws Exception {
        mockMvc.perform(get("/api/net-worth-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(9))))  // at least the original 9 types
                .andExpect(jsonPath("$[?(@.assetType == 'INDEX_FUND')]").exists())
                .andExpect(jsonPath("$[?(@.assetType == 'CRYPTO')]").exists())
                .andExpect(jsonPath("$[?(@.assetType == 'GOLD')]").exists());
    }

    @Test
    @WithMockUser
    void shouldToggleInclusionOff() throws Exception {
        // First call creates configs
        mockMvc.perform(get("/api/net-worth-config")).andExpect(status().isOk());

        NetWorthConfig crypto = repository.findByAssetType("CRYPTO").orElseThrow();
        crypto.setIncludeInNetWorth(false);

        mockMvc.perform(put("/api/net-worth-config/" + crypto.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(crypto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeInNetWorth", is(false)));
    }

    @Test
    @WithMockUser
    void shouldReturnIncludedTypes() throws Exception {
        // Setup
        mockMvc.perform(get("/api/net-worth-config")).andExpect(status().isOk());
        NetWorthConfig crypto = repository.findByAssetType("CRYPTO").orElseThrow();
        crypto.setIncludeInNetWorth(false);
        repository.save(crypto);

        mockMvc.perform(get("/api/net-worth-config/included-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasItem("CRYPTO"))))
                .andExpect(jsonPath("$", hasItem("INDEX_FUND")));
    }

    @Test
    @WithMockUser
    void shouldBatchUpdate() throws Exception {
        mockMvc.perform(get("/api/net-worth-config")).andExpect(status().isOk());
        List<NetWorthConfig> all = repository.findAll();
        all.forEach(c -> c.setIncludeInNetWorth(false));

        mockMvc.perform(put("/api/net-worth-config/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(all)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/net-worth-config/included-types"))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
