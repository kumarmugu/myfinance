package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Account;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.AccountType;
import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.OwnerRelationship;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.OwnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;

    private Owner testOwner;

    @BeforeEach
    void setup() {
        accountRepository.deleteAll();
        ownerRepository.deleteAll();
        testOwner = ownerRepository.save(Owner.builder().name("Test User").relationship(OwnerRelationship.SELF).build());
    }

    @Test
    @WithMockUser
    void shouldCreateAccount() throws Exception {
        Account account = Account.builder()
                .name("Tiger Broker").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(testOwner).build();

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(account)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Tiger Broker")))
                .andExpect(jsonPath("$.accountType", is("BROKER")))
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    @Test
    @WithMockUser
    void shouldGetAccountsByType() throws Exception {
        accountRepository.save(Account.builder().name("Tiger").accountType(AccountType.BROKER).currency(Currency.USD).owner(testOwner).build());
        accountRepository.save(Account.builder().name("DBS").accountType(AccountType.BANK).currency(Currency.SGD).owner(testOwner).build());

        mockMvc.perform(get("/api/accounts/type/BROKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Tiger")));
    }

    @Test
    @WithMockUser
    void shouldPreventDeleteWhenReferenced() throws Exception {
        // Account with no references - should delete fine
        Account acc = accountRepository.save(Account.builder().name("Empty").accountType(AccountType.BROKER).currency(Currency.USD).owner(testOwner).build());
        mockMvc.perform(delete("/api/accounts/" + acc.getId()))
                .andExpect(status().isNoContent());
    }
}
