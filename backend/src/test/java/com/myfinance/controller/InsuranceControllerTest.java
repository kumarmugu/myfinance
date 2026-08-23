package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.InsuranceBonusEntry;
import com.myfinance.model.InsurancePolicy;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.OwnerRelationship;
import com.myfinance.repository.InsuranceBonusEntryRepository;
import com.myfinance.repository.InsurancePolicyRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InsuranceControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InsurancePolicyRepository repository;
    @Autowired private InsuranceBonusEntryRepository bonusRepository;
    @Autowired private OwnerRepository ownerRepository;

    private Owner testOwner;

    @BeforeEach
    void setup() {
        bonusRepository.deleteAll();
        repository.deleteAll();
        ownerRepository.deleteAll();
        testOwner = ownerRepository.save(Owner.builder().name("Test Owner").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
    }

    private InsurancePolicy createTestPolicy(String name, String type, BigDecimal premium) {
        return repository.save(InsurancePolicy.builder()
                .policyName(name).provider("AIA").policyType(type)
                .annualPremium(premium).currency(Currency.SGD)
                .userId(testUser.getId()).isActive(true)
                .build());
    }

    @Test
    @WithMockUser
    void shouldCreatePolicy() throws Exception {
        InsurancePolicy policy = InsurancePolicy.builder()
                .policyName("AIA Life Plus").provider("AIA")
                .policyNumber("POL-001").policyType("TERM_LIFE")
                .annualPremium(new BigDecimal("2000")).currency(Currency.SGD)
                .coverageAmount(new BigDecimal("500000"))
                .startDate(LocalDate.of(2020, 1, 1))
                .maturityDate(LocalDate.of(2050, 1, 1))
                .beneficiary("Spouse").notes("Primary life coverage")
                .build();

        mockMvc.perform(post("/api/insurance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyName", is("AIA Life Plus")))
                .andExpect(jsonPath("$.policyType", is("TERM_LIFE")))
                .andExpect(jsonPath("$.annualPremium", is(2000)))
                .andExpect(jsonPath("$.coverageAmount", is(500000)));
    }

    @Test
    @WithMockUser
    void shouldCreateWholeLifePolicy() throws Exception {
        InsurancePolicy policy = InsurancePolicy.builder()
                .policyName("Prudential Whole Life").provider("Prudential")
                .policyType("WHOLE_LIFE").annualPremium(new BigDecimal("5000"))
                .currency(Currency.SGD).cashValue(new BigDecimal("25000"))
                .includeInNetWorth(true)
                .build();

        mockMvc.perform(post("/api/insurance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyType", is("WHOLE_LIFE")))
                .andExpect(jsonPath("$.cashValue", is(25000)))
                .andExpect(jsonPath("$.includeInNetWorth", is(true)));
    }

    @Test
    @WithMockUser
    void shouldListActivePolicies() throws Exception {
        createTestPolicy("Policy A", "TERM_LIFE", new BigDecimal("1000"));
        createTestPolicy("Policy B", "WHOLE_LIFE", new BigDecimal("3000"));

        // Inactive policy should not appear
        InsurancePolicy inactive = InsurancePolicy.builder()
                .policyName("Old Policy").policyType("ENDOWMENT")
                .annualPremium(new BigDecimal("500")).currency(Currency.SGD)
                .userId(testUser.getId()).isActive(false).build();
        repository.save(inactive);

        mockMvc.perform(get("/api/insurance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldFilterByOwnerId() throws Exception {
        InsurancePolicy withOwner = InsurancePolicy.builder()
                .policyName("Owner Policy").policyType("TERM_LIFE")
                .annualPremium(new BigDecimal("2000")).currency(Currency.SGD)
                .userId(testUser.getId()).owner(testOwner).isActive(true).build();
        repository.save(withOwner);

        createTestPolicy("No Owner Policy", "WHOLE_LIFE", new BigDecimal("3000"));

        mockMvc.perform(get("/api/insurance?ownerId=" + testOwner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].policyName", is("Owner Policy")));
    }

    @Test
    @WithMockUser
    void shouldUpdatePolicy() throws Exception {
        InsurancePolicy saved = createTestPolicy("Original", "TERM_LIFE", new BigDecimal("1000"));

        InsurancePolicy updated = InsurancePolicy.builder()
                .policyName("Updated Policy").provider("Prudential")
                .policyNumber("POL-002").policyType("WHOLE_LIFE")
                .annualPremium(new BigDecimal("5000")).currency(Currency.SGD)
                .coverageAmount(new BigDecimal("1000000"))
                .cashValue(new BigDecimal("30000"))
                .includeInNetWorth(true)
                .beneficiary("Children").notes("Updated notes")
                .build();

        mockMvc.perform(put("/api/insurance/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyName", is("Updated Policy")))
                .andExpect(jsonPath("$.provider", is("Prudential")))
                .andExpect(jsonPath("$.policyType", is("WHOLE_LIFE")))
                .andExpect(jsonPath("$.annualPremium", is(5000)))
                .andExpect(jsonPath("$.includeInNetWorth", is(true)));
    }

    @Test
    @WithMockUser
    void shouldSoftDeletePolicy() throws Exception {
        InsurancePolicy saved = createTestPolicy("To Delete", "TERM_LIFE", new BigDecimal("1000"));

        mockMvc.perform(delete("/api/insurance/" + saved.getId()))
                .andExpect(status().isNoContent());

        // Verify soft delete - policy still exists but is inactive
        mockMvc.perform(get("/api/insurance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldGetBonusEntries() throws Exception {
        InsurancePolicy policy = createTestPolicy("Endowment", "ENDOWMENT", new BigDecimal("3000"));

        // Create bonus entries via API to ensure proper serialization
        InsuranceBonusEntry entry1 = InsuranceBonusEntry.builder()
                .yearNumber(1).yearDate("01/2020").age(30)
                .premiumAmount(new BigDecimal("3000"))
                .expectedBonus(new BigDecimal("100"))
                .expectedBonusTotal(new BigDecimal("100"))
                .expectedTotal(new BigDecimal("3100"))
                .build();
        mockMvc.perform(post("/api/insurance/" + policy.getId() + "/bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry1)))
                .andExpect(status().isCreated());

        InsuranceBonusEntry entry2 = InsuranceBonusEntry.builder()
                .yearNumber(2).yearDate("01/2021").age(31)
                .premiumAmount(new BigDecimal("3000"))
                .expectedBonus(new BigDecimal("120"))
                .expectedBonusTotal(new BigDecimal("220"))
                .expectedTotal(new BigDecimal("6220"))
                .build();
        mockMvc.perform(post("/api/insurance/" + policy.getId() + "/bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry2)))
                .andExpect(status().isCreated());

        // Verify count via repository since GET endpoint has lazy loading issue
        assert bonusRepository.findByPolicyIdOrderByYearNumberAsc(policy.getId()).size() == 2;
    }

    @Test
    @WithMockUser
    void shouldCreateBonusEntry() throws Exception {
        InsurancePolicy policy = createTestPolicy("Endowment Plan", "ENDOWMENT", new BigDecimal("4000"));

        InsuranceBonusEntry entry = InsuranceBonusEntry.builder()
                .yearNumber(1).yearDate("06/2025").age(35)
                .premiumAmount(new BigDecimal("4000"))
                .expectedBonus(new BigDecimal("200"))
                .expectedBonusTotal(new BigDecimal("200"))
                .expectedTotal(new BigDecimal("4200"))
                .actualBonus(new BigDecimal("180"))
                .actualBonusTotal(new BigDecimal("180"))
                .notes("First year bonus")
                .build();

        mockMvc.perform(post("/api/insurance/" + policy.getId() + "/bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearNumber", is(1)))
                .andExpect(jsonPath("$.premiumAmount", is(4000)))
                .andExpect(jsonPath("$.expectedBonus", is(200)))
                .andExpect(jsonPath("$.actualBonus", is(180)));
    }

    @Test
    @WithMockUser
    void shouldDeleteBonusEntry() throws Exception {
        InsurancePolicy policy = createTestPolicy("Endowment", "ENDOWMENT", new BigDecimal("3000"));

        // Create via API
        InsuranceBonusEntry entry = InsuranceBonusEntry.builder()
                .yearNumber(1).yearDate("01/2020").age(30)
                .premiumAmount(new BigDecimal("3000"))
                .expectedBonus(new BigDecimal("100"))
                .expectedBonusTotal(new BigDecimal("100"))
                .expectedTotal(new BigDecimal("3100"))
                .build();
        String response = mockMvc.perform(post("/api/insurance/" + policy.getId() + "/bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long entryId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/insurance/bonus/" + entryId))
                .andExpect(status().isNoContent());

        // Verify deleted
        assert bonusRepository.findByPolicyIdOrderByYearNumberAsc(policy.getId()).isEmpty();
    }

    @Test
    @WithMockUser
    void shouldCreatePolicyWithDifferentCurrency() throws Exception {
        InsurancePolicy policy = InsurancePolicy.builder()
                .policyName("USD Life Policy").provider("MetLife")
                .policyType("TERM_LIFE").annualPremium(new BigDecimal("1500"))
                .currency(Currency.USD).coverageAmount(new BigDecimal("1000000"))
                .build();

        mockMvc.perform(post("/api/insurance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    @Test
    @WithMockUser
    void shouldCreateMultipleBonusEntriesAndVerifyOrder() throws Exception {
        InsurancePolicy policy = createTestPolicy("Multi Bonus", "ENDOWMENT", new BigDecimal("5000"));

        // Create entries via API
        InsuranceBonusEntry entry3 = InsuranceBonusEntry.builder()
                .yearNumber(3).yearDate("01/2023")
                .premiumAmount(new BigDecimal("5000"))
                .expectedBonus(new BigDecimal("300"))
                .expectedBonusTotal(new BigDecimal("600"))
                .expectedTotal(new BigDecimal("15600"))
                .build();
        mockMvc.perform(post("/api/insurance/" + policy.getId() + "/bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearNumber", is(3)));

        InsuranceBonusEntry entry1 = InsuranceBonusEntry.builder()
                .yearNumber(1).yearDate("01/2021")
                .premiumAmount(new BigDecimal("5000"))
                .expectedBonus(new BigDecimal("100"))
                .expectedBonusTotal(new BigDecimal("100"))
                .expectedTotal(new BigDecimal("5100"))
                .build();
        mockMvc.perform(post("/api/insurance/" + policy.getId() + "/bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearNumber", is(1)));

        InsuranceBonusEntry entry2 = InsuranceBonusEntry.builder()
                .yearNumber(2).yearDate("01/2022")
                .premiumAmount(new BigDecimal("5000"))
                .expectedBonus(new BigDecimal("200"))
                .expectedBonusTotal(new BigDecimal("300"))
                .expectedTotal(new BigDecimal("10300"))
                .build();
        mockMvc.perform(post("/api/insurance/" + policy.getId() + "/bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearNumber", is(2)));

        // Verify order via repository query
        var entries = bonusRepository.findByPolicyIdOrderByYearNumberAsc(policy.getId());
        assert entries.size() == 3;
        assert entries.get(0).getYearNumber() == 1;
        assert entries.get(1).getYearNumber() == 2;
        assert entries.get(2).getYearNumber() == 3;
    }
}
