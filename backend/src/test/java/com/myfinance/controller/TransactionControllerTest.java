package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
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
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private HoldingRepository holdingRepository;

    private Owner owner;
    private Account account;
    private Asset asset;

    @BeforeEach
    void setup() {
        holdingRepository.deleteAll();
        transactionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        owner = ownerRepository.save(Owner.builder().name("Test").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
        account = accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER).currency(Currency.SGD).owner(owner).userId(testUser.getId()).build());
        asset = assetRepository.save(Asset.builder().name("VOO").symbol("VOO-T").assetType(AssetType.INDEX_FUND).currency(Currency.USD).userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void shouldCreateBuyTransaction() throws Exception {
        Map<String, Object> req = Map.of("assetId", asset.getId(), "accountId", account.getId(), "ownerId", owner.getId(), "transactionType", "BUY", "quantity", 10, "pricePerUnit", 400, "transactionDate", "2024-01-15");
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType", is("BUY")));
    }

    @Test
    @WithMockUser
    void shouldListTransactions() throws Exception {
        Map<String, Object> req = Map.of("assetId", asset.getId(), "accountId", account.getId(), "ownerId", owner.getId(), "transactionType", "BUY", "quantity", 5, "pricePerUnit", 100, "transactionDate", "2024-06-01");
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/transactions")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser
    void shouldCreateHoldingOnBuy() throws Exception {
        Map<String, Object> req = Map.of("assetId", asset.getId(), "accountId", account.getId(), "ownerId", owner.getId(), "transactionType", "BUY", "quantity", 10, "pricePerUnit", 50, "transactionDate", "2024-03-01");
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/holdings")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].quantity", is(10.0)));
    }

    @Test
    @WithMockUser
    void shouldDeleteTransaction() throws Exception {
        Map<String, Object> req = Map.of("assetId", asset.getId(), "accountId", account.getId(), "ownerId", owner.getId(), "transactionType", "BUY", "quantity", 1, "pricePerUnit", 100, "transactionDate", "2024-01-01");
        String response = mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(delete("/api/transactions/" + id)).andExpect(status().isNoContent());
    }
}
