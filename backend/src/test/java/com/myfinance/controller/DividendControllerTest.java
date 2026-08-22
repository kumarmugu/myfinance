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
class DividendControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DividendRepository dividendRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;

    private Owner owner;
    private Account account;

    @BeforeEach
    void setup() {
        dividendRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        owner = ownerRepository.save(Owner.builder().name("Test").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
        account = accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER).currency(Currency.USD).owner(owner).userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void shouldCreateDividend() throws Exception {
        Dividend div = Dividend.builder().account(account).owner(owner).amount(new BigDecimal("150")).currency(Currency.USD).receivedDate(LocalDate.of(2024, 6, 15)).year(2024).quarter("Q2").instrument("VOO").build();
        mockMvc.perform(post("/api/dividends").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(div)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount", is(150)));
    }

    @Test
    @WithMockUser
    void shouldGetDividendSummary() throws Exception {
        dividendRepository.save(Dividend.builder().account(account).owner(owner).amount(new BigDecimal("100")).currency(Currency.USD).receivedDate(LocalDate.of(2024, 3, 1)).year(2024).quarter("Q1").instrument("DBS").userId(testUser.getId()).build());
        mockMvc.perform(get("/api/dividends/summary")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void shouldDeleteDividend() throws Exception {
        Dividend saved = dividendRepository.save(Dividend.builder().account(account).owner(owner).amount(new BigDecimal("50")).currency(Currency.USD).receivedDate(LocalDate.now()).year(2024).quarter("Q1").instrument("TEST").userId(testUser.getId()).build());
        mockMvc.perform(delete("/api/dividends/" + saved.getId())).andExpect(status().isNoContent());
    }
}
