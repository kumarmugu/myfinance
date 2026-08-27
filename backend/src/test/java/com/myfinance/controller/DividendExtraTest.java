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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Additional Dividend tests covering filter gaps not exercised by DividendControllerTest:
 * getByOwner (?ownerId=) and getByAccount (?accountId=).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DividendExtraTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DividendRepository dividendRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;

    private Owner ownerA;
    private Owner ownerB;
    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setup() {
        dividendRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        ownerA = ownerRepository.save(Owner.builder().name("Owner A").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
        ownerB = ownerRepository.save(Owner.builder().name("Owner B").relationship(OwnerRelationship.SPOUSE).userId(testUser.getId()).build());
        accountA = accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER).currency(Currency.USD).owner(ownerA).userId(testUser.getId()).build());
        accountB = accountRepository.save(Account.builder().name("IBKR").accountType(AccountType.BROKER).currency(Currency.USD).owner(ownerB).userId(testUser.getId()).build());
    }

    private Dividend div(Owner owner, Account account, String instrument) {
        return dividendRepository.save(Dividend.builder()
                .owner(owner).account(account)
                .amount(new BigDecimal("100")).currency(Currency.USD)
                .receivedDate(LocalDate.of(2024, 3, 1)).year(2024).quarter("Q1")
                .instrument(instrument).userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void shouldFilterByOwner() throws Exception {
        div(ownerA, accountA, "AAA");
        div(ownerB, accountB, "BBB");

        mockMvc.perform(get("/api/dividends").param("ownerId", ownerA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].instrument", is("AAA")));
    }

    @Test
    @WithMockUser
    void shouldFilterByAccount() throws Exception {
        div(ownerA, accountA, "AAA");
        div(ownerB, accountB, "BBB");

        mockMvc.perform(get("/api/dividends").param("accountId", accountB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].instrument", is("BBB")));
    }
}
