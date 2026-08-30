package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.BankSavings;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.OwnerRelationship;
import com.myfinance.repository.BankSavingsRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Additional BankSavings tests covering gaps not exercised by BankSavingsControllerTest:
 * update (PUT) and updateBalance (PATCH /{id}/balance).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BankSavingsExtraTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BankSavingsRepository repository;
    @Autowired private OwnerRepository ownerRepository;

    private Owner owner;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        owner = ownerRepository.save(Owner.builder()
                .name("Self").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void shouldUpdateBankSavings() throws Exception {
        BankSavings saved = repository.save(BankSavings.builder()
                .accountName("Old Account").bankName("DBS").balance(new BigDecimal("1000"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true)
                .owner(owner).userId(testUser.getId()).build());

        BankSavings update = BankSavings.builder()
                .accountName("New Account").bankName("OCBC").balance(new BigDecimal("2500"))
                .currency(Currency.USD).country("Singapore").includeInNetWorth(true).owner(owner).build();

        mockMvc.perform(put("/api/bank-savings/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountName", is("New Account")))
                .andExpect(jsonPath("$.bankName", is("OCBC")))
                .andExpect(jsonPath("$.balance", is(2500)));
    }

    @Test
    @WithMockUser
    void shouldUpdateBalance() throws Exception {
        BankSavings saved = repository.save(BankSavings.builder()
                .accountName("Savings").bankName("DBS").balance(new BigDecimal("1000"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true)
                .userId(testUser.getId()).build());

        mockMvc.perform(patch("/api/bank-savings/" + saved.getId() + "/balance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\":7777.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(7777.50)));
    }

    @Test
    @WithMockUser
    void shouldSetLastUpdatedOnCreate() throws Exception {
        // Client sends no lastUpdated; the server must stamp it with today's date.
        BankSavings newAccount = BankSavings.builder()
                .accountName("Fresh Account").bankName("DBS").balance(new BigDecimal("500"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true).owner(owner).build();

        String today = java.time.LocalDate.now().toString();

        mockMvc.perform(post("/api/bank-savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAccount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastUpdated", is(today)));
    }

    @Test
    @WithMockUser
    void shouldRefreshLastUpdatedOnUpdate() throws Exception {
        // Existing record has a stale lastUpdated; the client sends none on update.
        BankSavings saved = repository.save(BankSavings.builder()
                .accountName("Acct").bankName("DBS").balance(new BigDecimal("1000"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true)
                .lastUpdated(java.time.LocalDate.of(2000, 1, 1))
                .owner(owner).userId(testUser.getId()).build());

        BankSavings update = BankSavings.builder()
                .accountName("Acct").bankName("DBS").balance(new BigDecimal("1200"))
                .currency(Currency.SGD).country("Singapore").includeInNetWorth(true).owner(owner).build();

        String today = java.time.LocalDate.now().toString();

        mockMvc.perform(put("/api/bank-savings/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdated", is(today)));
    }
}
