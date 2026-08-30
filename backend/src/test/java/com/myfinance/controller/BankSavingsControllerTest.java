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

@SpringBootTest
@AutoConfigureMockMvc
class BankSavingsControllerTest extends BaseControllerTest {

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
    void shouldCreateBankSavings() throws Exception {
        BankSavings savings = BankSavings.builder().accountName("DBS Savings").bankName("DBS").balance(new BigDecimal("50000")).currency(Currency.SGD).country("Singapore").includeInNetWorth(true).owner(owner).build();
        mockMvc.perform(post("/api/bank-savings").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(savings)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountName", is("DBS Savings")))
                .andExpect(jsonPath("$.owner.id", is(owner.getId().intValue())));
    }

    @Test
    @WithMockUser
    void shouldRejectCreateWithoutOwner() throws Exception {
        BankSavings savings = BankSavings.builder().accountName("No Owner").bankName("DBS").balance(new BigDecimal("100")).currency(Currency.SGD).country("Singapore").build();
        mockMvc.perform(post("/api/bank-savings").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(savings)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldFilterByOwner() throws Exception {
        Owner spouse = ownerRepository.save(Owner.builder().name("Spouse").relationship(OwnerRelationship.SPOUSE).userId(testUser.getId()).build());
        repository.save(BankSavings.builder().accountName("Mine").bankName("DBS").balance(new BigDecimal("1000")).currency(Currency.SGD).country("Singapore").userId(testUser.getId()).owner(owner).build());
        repository.save(BankSavings.builder().accountName("Hers").bankName("OCBC").balance(new BigDecimal("2000")).currency(Currency.SGD).country("Singapore").userId(testUser.getId()).owner(spouse).build());

        mockMvc.perform(get("/api/bank-savings").param("ownerId", owner.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountName", is("Mine")));
    }

    @Test
    @WithMockUser
    void shouldListByUser() throws Exception {
        repository.save(BankSavings.builder().accountName("A").bankName("DBS").balance(new BigDecimal("1000")).currency(Currency.SGD).country("Singapore").userId(testUser.getId()).owner(owner).build());
        mockMvc.perform(get("/api/bank-savings")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser
    void shouldToggleNetWorth() throws Exception {
        BankSavings saved = repository.save(BankSavings.builder().accountName("Test").bankName("X").balance(new BigDecimal("100")).currency(Currency.SGD).country("Singapore").includeInNetWorth(true).userId(testUser.getId()).build());
        mockMvc.perform(patch("/api/bank-savings/" + saved.getId() + "/net-worth")).andExpect(status().isOk()).andExpect(jsonPath("$.includeInNetWorth", is(false)));
    }

    @Test
    @WithMockUser
    void shouldGetSummary() throws Exception {
        repository.save(BankSavings.builder().accountName("A").bankName("DBS").balance(new BigDecimal("10000")).currency(Currency.SGD).country("Singapore").includeInNetWorth(true).userId(testUser.getId()).build());
        mockMvc.perform(get("/api/bank-savings/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totalAccounts", is(1)));
    }
}
