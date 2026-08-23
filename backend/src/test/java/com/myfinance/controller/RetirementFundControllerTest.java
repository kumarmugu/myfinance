package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Owner;
import com.myfinance.model.RetirementFundEntry;
import com.myfinance.model.enums.OwnerRelationship;
import com.myfinance.repository.OwnerRepository;
import com.myfinance.repository.RetirementFundEntryRepository;
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
class RetirementFundControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RetirementFundEntryRepository repository;
    @Autowired private OwnerRepository ownerRepository;

    private Owner testOwner;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        ownerRepository.deleteAll();
        testOwner = ownerRepository.save(Owner.builder().name("Test Owner").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void shouldCreateCPFContribution() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("CPF").entryType("CONTRIBUTION")
                .amount(new BigDecimal("2500")).entryDate(LocalDate.of(2026, 1, 15))
                .account("OA").employer("BCS").build();

        mockMvc.perform(post("/api/retirement-fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fundType", is("CPF")))
                .andExpect(jsonPath("$.entryType", is("CONTRIBUTION")))
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(1)));
    }

    @Test
    @WithMockUser
    void shouldCreateSRSContribution() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("SRS").entryType("CONTRIBUTION")
                .amount(new BigDecimal("15300")).entryDate(LocalDate.of(2026, 3, 1))
                .account("SRS").build();

        mockMvc.perform(post("/api/retirement-fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fundType", is("SRS")))
                .andExpect(jsonPath("$.amount", is(15300)));
    }

    @Test
    @WithMockUser
    void shouldCreateWithdrawal() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("CPF").entryType("WITHDRAWAL")
                .amount(new BigDecimal("50000")).entryDate(LocalDate.of(2026, 5, 1))
                .account("OA").notes("Housing withdrawal").build();

        mockMvc.perform(post("/api/retirement-fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryType", is("WITHDRAWAL")))
                .andExpect(jsonPath("$.notes", is("Housing withdrawal")));
    }

    @Test
    @WithMockUser
    void shouldCreateInterestEntry() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("CPF").entryType("INTEREST")
                .amount(new BigDecimal("1200")).entryDate(LocalDate.of(2026, 1, 1))
                .account("OA").balance(new BigDecimal("85000")).build();

        mockMvc.perform(post("/api/retirement-fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryType", is("INTEREST")))
                .andExpect(jsonPath("$.balance", is(85000)));
    }

    @Test
    @WithMockUser
    void shouldCreateEmployerContribution() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("EPF").entryType("EMPLOYER_CONTRIBUTION")
                .amount(new BigDecimal("3000")).entryDate(LocalDate.of(2026, 2, 28))
                .employer("ABC Corp").build();

        mockMvc.perform(post("/api/retirement-fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryType", is("EMPLOYER_CONTRIBUTION")))
                .andExpect(jsonPath("$.employer", is("ABC Corp")));
    }

    @Test
    @WithMockUser
    void shouldListAllEntries() throws Exception {
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2000")).entryDate(LocalDate.now()).userId(testUser.getId()).build());
        repository.save(RetirementFundEntry.builder().fundType("SRS").entryType("CONTRIBUTION").amount(new BigDecimal("15300")).entryDate(LocalDate.now()).userId(testUser.getId()).build());
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("INTEREST").amount(new BigDecimal("500")).entryDate(LocalDate.now()).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/retirement-fund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @WithMockUser
    void shouldFilterByFundType() throws Exception {
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2000")).entryDate(LocalDate.now()).userId(testUser.getId()).build());
        repository.save(RetirementFundEntry.builder().fundType("SRS").entryType("CONTRIBUTION").amount(new BigDecimal("15300")).entryDate(LocalDate.now()).userId(testUser.getId()).build());
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("INTEREST").amount(new BigDecimal("500")).entryDate(LocalDate.now()).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/retirement-fund?fundType=CPF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fundType", is("CPF")))
                .andExpect(jsonPath("$[1].fundType", is("CPF")));
    }

    @Test
    @WithMockUser
    void shouldFilterByOwnerId() throws Exception {
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2000")).entryDate(LocalDate.now()).owner(testOwner).userId(testUser.getId()).build());
        repository.save(RetirementFundEntry.builder().fundType("SRS").entryType("CONTRIBUTION").amount(new BigDecimal("5000")).entryDate(LocalDate.now()).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/retirement-fund?ownerId=" + testOwner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fundType", is("CPF")));
    }

    @Test
    @WithMockUser
    void shouldGetSummary() throws Exception {
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2000")).entryDate(LocalDate.now()).userId(testUser.getId()).build());
        repository.save(RetirementFundEntry.builder().fundType("CPF").entryType("INTEREST").amount(new BigDecimal("500")).entryDate(LocalDate.now()).userId(testUser.getId()).build());
        repository.save(RetirementFundEntry.builder().fundType("SRS").entryType("CONTRIBUTION").amount(new BigDecimal("15300")).entryDate(LocalDate.now()).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/retirement-fund/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byFund").exists())
                .andExpect(jsonPath("$.totalEntries", is(3)));
    }

    @Test
    @WithMockUser
    void shouldUpdateEntry() throws Exception {
        RetirementFundEntry saved = repository.save(RetirementFundEntry.builder()
                .fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2000"))
                .entryDate(LocalDate.of(2026, 1, 15)).account("OA").userId(testUser.getId()).build());

        RetirementFundEntry updated = RetirementFundEntry.builder()
                .fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2500"))
                .entryDate(LocalDate.of(2026, 1, 15)).account("SA").employer("New Corp")
                .year(2026).month(1).notes("Updated").build();

        mockMvc.perform(put("/api/retirement-fund/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(2500)))
                .andExpect(jsonPath("$.account", is("SA")))
                .andExpect(jsonPath("$.employer", is("New Corp")))
                .andExpect(jsonPath("$.notes", is("Updated")));
    }

    @Test
    @WithMockUser
    void shouldDeleteEntry() throws Exception {
        RetirementFundEntry saved = repository.save(RetirementFundEntry.builder()
                .fundType("CPF").entryType("CONTRIBUTION").amount(new BigDecimal("2000"))
                .entryDate(LocalDate.now()).userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/retirement-fund/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/retirement-fund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldAutoPopulateYearAndMonth() throws Exception {
        RetirementFundEntry entry = RetirementFundEntry.builder()
                .fundType("CPF").entryType("CONTRIBUTION")
                .amount(new BigDecimal("2500")).entryDate(LocalDate.of(2026, 7, 20))
                .build();

        mockMvc.perform(post("/api/retirement-fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(7)));
    }
}
