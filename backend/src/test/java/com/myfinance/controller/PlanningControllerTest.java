package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Account;
import com.myfinance.model.AccountDeposit;
import com.myfinance.model.AllocationTarget;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.AccountType;
import com.myfinance.model.enums.AssetType;
import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.OwnerRelationship;
import com.myfinance.repository.AccountDepositRepository;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.AllocationTargetRepository;
import com.myfinance.repository.OwnerRepository;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlanningControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AllocationTargetRepository allocationTargetRepository;
    @Autowired private AccountDepositRepository accountDepositRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;

    private Owner testOwner;
    private Account testAccount;

    @BeforeEach
    void setup() {
        accountDepositRepository.deleteAll();
        allocationTargetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        testOwner = ownerRepository.save(Owner.builder().name("Test Owner").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
        testAccount = accountRepository.save(Account.builder().name("DBS Savings").accountType(AccountType.BANK).currency(Currency.SGD).owner(testOwner).userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void shouldGetAllocation() throws Exception {
        mockMvc.perform(get("/api/planning/allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets").exists())
                .andExpect(jsonPath("$.current").exists());
    }

    @Test
    @WithMockUser
    void shouldGetAllocationWithTargets() throws Exception {
        allocationTargetRepository.save(AllocationTarget.builder()
                .userId(testUser.getId()).owner(testOwner)
                .assetType(AssetType.INDEX_FUND)
                .targetPercentage(new BigDecimal("40.00"))
                .targetAmount(new BigDecimal("100000"))
                .build());
        allocationTargetRepository.save(AllocationTarget.builder()
                .userId(testUser.getId()).owner(testOwner)
                .assetType(AssetType.FIXED_DEPOSIT)
                .targetPercentage(new BigDecimal("30.00"))
                .targetAmount(new BigDecimal("75000"))
                .build());

        mockMvc.perform(get("/api/planning/allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldFilterAllocationByOwnerId() throws Exception {
        Owner anotherOwner = ownerRepository.save(Owner.builder().name("Spouse").relationship(OwnerRelationship.SPOUSE).userId(testUser.getId()).build());
        allocationTargetRepository.save(AllocationTarget.builder()
                .userId(testUser.getId()).owner(testOwner)
                .assetType(AssetType.INDEX_FUND)
                .targetPercentage(new BigDecimal("50.00")).build());
        allocationTargetRepository.save(AllocationTarget.builder()
                .userId(testUser.getId()).owner(anotherOwner)
                .assetType(AssetType.GOLD)
                .targetPercentage(new BigDecimal("20.00")).build());

        mockMvc.perform(get("/api/planning/allocation?ownerId=" + testOwner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets", hasSize(1)))
                .andExpect(jsonPath("$.targets[0].assetType", is("INDEX_FUND")));
    }

    @Test
    @WithMockUser
    void shouldUpdateAllocationTargets() throws Exception {
        AllocationTarget target1 = AllocationTarget.builder()
                .userId(testUser.getId()).owner(testOwner)
                .assetType(AssetType.INDEX_FUND)
                .targetPercentage(new BigDecimal("40.00"))
                .targetAmount(new BigDecimal("100000"))
                .build();
        AllocationTarget target2 = AllocationTarget.builder()
                .userId(testUser.getId()).owner(testOwner)
                .assetType(AssetType.FIXED_DEPOSIT)
                .targetPercentage(new BigDecimal("30.00"))
                .targetAmount(new BigDecimal("75000"))
                .build();
        AllocationTarget target3 = AllocationTarget.builder()
                .userId(testUser.getId()).owner(testOwner)
                .assetType(AssetType.CRYPTO)
                .targetPercentage(new BigDecimal("10.00"))
                .targetAmount(new BigDecimal("25000"))
                .build();

        List<AllocationTarget> targets = List.of(target1, target2, target3);

        mockMvc.perform(put("/api/planning/allocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targets)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].assetType", is("INDEX_FUND")))
                .andExpect(jsonPath("$[0].targetPercentage", is(40.00)));
    }

    @Test
    @WithMockUser
    void shouldGetDeposits() throws Exception {
        accountDepositRepository.save(AccountDeposit.builder()
                .account(testAccount).amount(new BigDecimal("5000"))
                .depositType("DEPOSIT").currency(Currency.SGD)
                .depositDate(LocalDate.of(2025, 1, 15))
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/planning/deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount", closeTo(5000, 0.01)))
                .andExpect(jsonPath("$[0].depositType", is("DEPOSIT")));
    }

    @Test
    @WithMockUser
    void shouldFilterDepositsByAccountId() throws Exception {
        Account anotherAccount = accountRepository.save(Account.builder().name("OCBC Savings").accountType(AccountType.BANK).currency(Currency.SGD).owner(testOwner).userId(testUser.getId()).build());

        accountDepositRepository.save(AccountDeposit.builder()
                .account(testAccount).amount(new BigDecimal("5000"))
                .depositType("DEPOSIT").currency(Currency.SGD)
                .depositDate(LocalDate.of(2025, 1, 15))
                .userId(testUser.getId()).build());
        accountDepositRepository.save(AccountDeposit.builder()
                .account(anotherAccount).amount(new BigDecimal("3000"))
                .depositType("DEPOSIT").currency(Currency.SGD)
                .depositDate(LocalDate.of(2025, 2, 15))
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/planning/deposits?accountId=" + testAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount", closeTo(5000, 0.01)));
    }

    @Test
    @WithMockUser
    void shouldCreateDeposit() throws Exception {
        AccountDeposit deposit = AccountDeposit.builder()
                .account(testAccount)
                .amount(new BigDecimal("10000"))
                .depositType("DEPOSIT")
                .currency(Currency.SGD)
                .depositDate(LocalDate.of(2025, 3, 1))
                .notes("Monthly savings")
                .build();

        mockMvc.perform(post("/api/planning/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount", is(10000)))
                .andExpect(jsonPath("$.depositType", is("DEPOSIT")))
                .andExpect(jsonPath("$.notes", is("Monthly savings")));
    }

    @Test
    @WithMockUser
    void shouldCreateWithdrawal() throws Exception {
        AccountDeposit withdrawal = AccountDeposit.builder()
                .account(testAccount)
                .amount(new BigDecimal("2000"))
                .depositType("WITHDRAWAL")
                .currency(Currency.SGD)
                .depositDate(LocalDate.of(2025, 4, 1))
                .notes("Emergency expense")
                .build();

        mockMvc.perform(post("/api/planning/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depositType", is("WITHDRAWAL")))
                .andExpect(jsonPath("$.amount", is(2000)));
    }

    @Test
    @WithMockUser
    void shouldDeleteDeposit() throws Exception {
        AccountDeposit saved = accountDepositRepository.save(AccountDeposit.builder()
                .account(testAccount).amount(new BigDecimal("5000"))
                .depositType("DEPOSIT").currency(Currency.SGD)
                .depositDate(LocalDate.of(2025, 1, 15))
                .userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/planning/deposits/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/planning/deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldCreateDepositWithUSDCurrency() throws Exception {
        Account usdAccount = accountRepository.save(Account.builder().name("Tiger Broker").accountType(AccountType.BROKER).currency(Currency.USD).owner(testOwner).userId(testUser.getId()).build());

        AccountDeposit deposit = AccountDeposit.builder()
                .account(usdAccount)
                .amount(new BigDecimal("5000"))
                .depositType("DEPOSIT")
                .currency(Currency.USD)
                .depositDate(LocalDate.of(2025, 5, 1))
                .build();

        mockMvc.perform(post("/api/planning/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency", is("USD")));
    }
}
