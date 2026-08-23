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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SoldPositionControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SoldPositionRepository soldPositionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;

    private Asset asset;
    private Account account;
    private Owner owner;

    @BeforeEach
    void setup() {
        soldPositionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();

        owner = ownerRepository.save(Owner.builder()
                .name("Test Owner").relationship(OwnerRelationship.SELF)
                .userId(testUser.getId()).build());
        account = accountRepository.save(Account.builder()
                .name("Tiger Broker").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner)
                .userId(testUser.getId()).build());
        asset = assetRepository.save(Asset.builder()
                .name("Apple Inc").symbol("AAPL-SP")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD)
                .userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser(username = "user")
    void shouldCreateSoldPosition() throws Exception {
        SoldPosition sp = SoldPosition.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(BigDecimal.TEN)
                .buyPrice(new BigDecimal("150.00"))
                .sellPrice(new BigDecimal("180.00"))
                .investedAmount(new BigDecimal("1500.00"))
                .soldAmount(new BigDecimal("1800.00"))
                .profit(new BigDecimal("300.00"))
                .profitPercentage(new BigDecimal("20.00"))
                .currency(Currency.USD)
                .investedDate(LocalDate.of(2024, 1, 15))
                .soldDate(LocalDate.of(2024, 6, 15))
                .isShortTerm(false)
                .purpose(InvestmentPurpose.LONG_TERM)
                .build();

        mockMvc.perform(post("/api/sold-positions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity", is(10)))
                .andExpect(jsonPath("$.buyPrice", is(150.00)))
                .andExpect(jsonPath("$.sellPrice", is(180.00)))
                .andExpect(jsonPath("$.profit", is(300.00)));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldListSoldPositions() throws Exception {
        soldPositionRepository.save(SoldPosition.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(BigDecimal.valueOf(5))
                .buyPrice(new BigDecimal("100.00"))
                .sellPrice(new BigDecimal("120.00"))
                .investedAmount(new BigDecimal("500.00"))
                .soldAmount(new BigDecimal("600.00"))
                .profit(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .investedDate(LocalDate.of(2024, 2, 1))
                .soldDate(LocalDate.of(2024, 5, 1))
                .isShortTerm(false)
                .userId(testUser.getId())
                .build());

        mockMvc.perform(get("/api/sold-positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].quantity", is(5.0)));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldGetShortTermTrades() throws Exception {
        // Short-term trade
        soldPositionRepository.save(SoldPosition.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(BigDecimal.valueOf(20))
                .buyPrice(new BigDecimal("50.00"))
                .sellPrice(new BigDecimal("55.00"))
                .investedAmount(new BigDecimal("1000.00"))
                .soldAmount(new BigDecimal("1100.00"))
                .profit(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .investedDate(LocalDate.of(2024, 5, 1))
                .soldDate(LocalDate.of(2024, 5, 15))
                .isShortTerm(true)
                .purpose(InvestmentPurpose.TRADING)
                .userId(testUser.getId())
                .build());

        // Long-term trade (should not appear)
        soldPositionRepository.save(SoldPosition.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(BigDecimal.valueOf(10))
                .buyPrice(new BigDecimal("80.00"))
                .sellPrice(new BigDecimal("100.00"))
                .investedAmount(new BigDecimal("800.00"))
                .soldAmount(new BigDecimal("1000.00"))
                .profit(new BigDecimal("200.00"))
                .currency(Currency.USD)
                .investedDate(LocalDate.of(2023, 1, 1))
                .soldDate(LocalDate.of(2024, 6, 1))
                .isShortTerm(false)
                .purpose(InvestmentPurpose.LONG_TERM)
                .userId(testUser.getId())
                .build());

        mockMvc.perform(get("/api/sold-positions/short-term"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isShortTerm", is(true)));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldDeleteSoldPosition() throws Exception {
        SoldPosition sp = soldPositionRepository.save(SoldPosition.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(BigDecimal.valueOf(3))
                .buyPrice(new BigDecimal("200.00"))
                .sellPrice(new BigDecimal("250.00"))
                .investedAmount(new BigDecimal("600.00"))
                .soldAmount(new BigDecimal("750.00"))
                .profit(new BigDecimal("150.00"))
                .currency(Currency.USD)
                .investedDate(LocalDate.of(2024, 1, 1))
                .soldDate(LocalDate.of(2024, 4, 1))
                .isShortTerm(false)
                .userId(testUser.getId())
                .build());

        mockMvc.perform(delete("/api/sold-positions/" + sp.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sold-positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldFilterByOwner() throws Exception {
        soldPositionRepository.save(SoldPosition.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(BigDecimal.valueOf(7))
                .buyPrice(new BigDecimal("90.00"))
                .sellPrice(new BigDecimal("110.00"))
                .investedAmount(new BigDecimal("630.00"))
                .soldAmount(new BigDecimal("770.00"))
                .profit(new BigDecimal("140.00"))
                .currency(Currency.USD)
                .investedDate(LocalDate.of(2024, 3, 1))
                .soldDate(LocalDate.of(2024, 6, 1))
                .isShortTerm(false)
                .userId(testUser.getId())
                .build());

        mockMvc.perform(get("/api/sold-positions").param("ownerId", owner.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
