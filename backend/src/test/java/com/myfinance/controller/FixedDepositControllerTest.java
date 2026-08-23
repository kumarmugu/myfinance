package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Bank;
import com.myfinance.model.FDHolder;
import com.myfinance.model.FixedDeposit;
import com.myfinance.model.enums.FDStatus;
import com.myfinance.repository.BankRepository;
import com.myfinance.repository.FDHolderRepository;
import com.myfinance.repository.FixedDepositRepository;
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
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FixedDepositControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FixedDepositRepository fdRepository;
    @Autowired private BankRepository bankRepository;
    @Autowired private FDHolderRepository fdHolderRepository;

    private Bank testBank;
    private FDHolder testHolder;

    @BeforeEach
    void setup() {
        fdRepository.deleteAll();
        fdHolderRepository.deleteAll();
        bankRepository.deleteAll();
        testBank = bankRepository.save(Bank.builder().name("DBS Bank").shortName("DBS").country("Singapore").build());
        testHolder = fdHolderRepository.save(FDHolder.builder().name("John Doe").relationship("SELF").isSeniorCitizen(false).build());
    }

    private FixedDeposit createTestFD(BigDecimal principal, BigDecimal rate, LocalDate start, LocalDate maturity, FDStatus status) {
        return fdRepository.save(FixedDeposit.builder()
                .holder(testHolder).bank(testBank)
                .principalAmount(principal).interestRate(rate)
                .startDate(start).maturityDate(maturity)
                .status(status).userId(testUser.getId())
                .build());
    }

    @Test
    @WithMockUser
    void shouldListAllFixedDeposits() throws Exception {
        createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);
        createTestFD(new BigDecimal("200000"), new BigDecimal("6.0"), LocalDate.of(2024, 6, 1), LocalDate.of(2025, 6, 1), FDStatus.ACTIVE);

        mockMvc.perform(get("/api/fixed-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldGetFixedDepositById() throws Exception {
        FixedDeposit fd = createTestFD(new BigDecimal("150000"), new BigDecimal("5.0"), LocalDate.of(2024, 3, 1), LocalDate.of(2025, 3, 1), FDStatus.ACTIVE);

        mockMvc.perform(get("/api/fixed-deposits/" + fd.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalAmount", closeTo(150000, 0.01)))
                .andExpect(jsonPath("$.interestRate", closeTo(5.0, 0.01)));
    }

    @Test
    @WithMockUser
    void shouldFilterByHolder() throws Exception {
        FDHolder anotherHolder = fdHolderRepository.save(FDHolder.builder().name("Jane Doe").relationship("SPOUSE").isSeniorCitizen(true).build());
        createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);
        fdRepository.save(FixedDeposit.builder().holder(anotherHolder).bank(testBank)
                .principalAmount(new BigDecimal("200000")).interestRate(new BigDecimal("6.0"))
                .startDate(LocalDate.of(2024, 1, 1)).maturityDate(LocalDate.of(2025, 1, 1))
                .status(FDStatus.ACTIVE).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/fixed-deposits?holderId=" + testHolder.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].holder.name", is("John Doe")));
    }

    @Test
    @WithMockUser
    void shouldFilterByBank() throws Exception {
        Bank anotherBank = bankRepository.save(Bank.builder().name("OCBC Bank").shortName("OCBC").country("Singapore").build());
        createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);
        fdRepository.save(FixedDeposit.builder().holder(testHolder).bank(anotherBank)
                .principalAmount(new BigDecimal("200000")).interestRate(new BigDecimal("6.0"))
                .startDate(LocalDate.of(2024, 1, 1)).maturityDate(LocalDate.of(2025, 1, 1))
                .status(FDStatus.ACTIVE).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/fixed-deposits?bankId=" + testBank.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bank.shortName", is("DBS")));
    }

    @Test
    @WithMockUser
    void shouldFilterByStatus() throws Exception {
        createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);
        createTestFD(new BigDecimal("200000"), new BigDecimal("6.0"), LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1), FDStatus.MATURED);

        mockMvc.perform(get("/api/fixed-deposits?status=MATURED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("MATURED")));
    }

    @Test
    @WithMockUser
    void shouldGetMaturingDeposits() throws Exception {
        // Maturing within 90 days
        createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.now().minusMonths(11), LocalDate.now().plusDays(30), FDStatus.ACTIVE);
        // Not maturing soon
        createTestFD(new BigDecimal("200000"), new BigDecimal("6.0"), LocalDate.now(), LocalDate.now().plusDays(200), FDStatus.ACTIVE);

        mockMvc.perform(get("/api/fixed-deposits/maturing?days=90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser
    void shouldGetRequiresUpdateDeposits() throws Exception {
        FixedDeposit fd = createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);
        fd.setRequiresUpdate(true);
        fdRepository.save(fd);

        createTestFD(new BigDecimal("200000"), new BigDecimal("6.0"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 6, 1), FDStatus.ACTIVE);

        mockMvc.perform(get("/api/fixed-deposits/requires-update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser
    void shouldGetSummary() throws Exception {
        createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6), FDStatus.ACTIVE);
        createTestFD(new BigDecimal("200000"), new BigDecimal("6.0"), LocalDate.now().minusMonths(3), LocalDate.now().plusMonths(9), FDStatus.ACTIVE);
        createTestFD(new BigDecimal("50000"), new BigDecimal("4.0"), LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1), FDStatus.MATURED);

        mockMvc.perform(get("/api/fixed-deposits/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFDs", is(2)))
                .andExpect(jsonPath("$.totalPrincipal", closeTo(300000, 0.01)))
                .andExpect(jsonPath("$.byBank").exists())
                .andExpect(jsonPath("$.maturingWithin30Days").exists())
                .andExpect(jsonPath("$.maturingWithin90Days").exists())
                .andExpect(jsonPath("$.requiresUpdate").exists())
                .andExpect(jsonPath("$.includedInNetWorth").exists());
    }

    @Test
    @WithMockUser
    void shouldCreateFixedDeposit() throws Exception {
        FixedDeposit fd = FixedDeposit.builder()
                .holder(testHolder).bank(testBank)
                .principalAmount(new BigDecimal("500000"))
                .interestRate(new BigDecimal("7.0"))
                .startDate(LocalDate.of(2025, 1, 1))
                .maturityDate(LocalDate.of(2026, 1, 1))
                .period("12 Months").branch("Main Branch")
                .status(FDStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/fixed-deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principalAmount", is(500000)))
                .andExpect(jsonPath("$.interestRate", is(7.0)))
                .andExpect(jsonPath("$.period", is("12 Months")))
                .andExpect(jsonPath("$.expectedInterest").exists());
    }

    @Test
    @WithMockUser
    void shouldUpdateFixedDeposit() throws Exception {
        FixedDeposit fd = createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);

        FixedDeposit updated = FixedDeposit.builder()
                .holder(testHolder).bank(testBank)
                .principalAmount(new BigDecimal("150000"))
                .interestRate(new BigDecimal("6.0"))
                .startDate(LocalDate.of(2024, 1, 1))
                .maturityDate(LocalDate.of(2025, 6, 1))
                .status(FDStatus.ACTIVE)
                .build();

        mockMvc.perform(put("/api/fixed-deposits/" + fd.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalAmount", is(150000)))
                .andExpect(jsonPath("$.interestRate", is(6.0)));
    }

    @Test
    @WithMockUser
    void shouldDeleteFixedDeposit() throws Exception {
        FixedDeposit fd = createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);

        mockMvc.perform(delete("/api/fixed-deposits/" + fd.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fixed-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldToggleNetWorthInclusion() throws Exception {
        FixedDeposit fd = createTestFD(new BigDecimal("100000"), new BigDecimal("5.5"), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), FDStatus.ACTIVE);

        mockMvc.perform(patch("/api/fixed-deposits/" + fd.getId() + "/net-worth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("includeInNetWorth", true, "netWorthAmount", 100000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeInNetWorth", is(true)))
                .andExpect(jsonPath("$.netWorthAmount", is(100000)));
    }

    @Test
    @WithMockUser
    void shouldListBanks() throws Exception {
        mockMvc.perform(get("/api/fixed-deposits/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].shortName", is("DBS")));
    }

    @Test
    @WithMockUser
    void shouldListHolders() throws Exception {
        mockMvc.perform(get("/api/fixed-deposits/holders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("John Doe")));
    }

    @Test
    @WithMockUser
    void shouldCreateBank() throws Exception {
        Bank bank = Bank.builder().name("POSB Bank").shortName("POSB").country("Singapore").build();

        mockMvc.perform(post("/api/fixed-deposits/banks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bank)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortName", is("POSB")))
                .andExpect(jsonPath("$.country", is("Singapore")));
    }

    @Test
    @WithMockUser
    void shouldCreateHolder() throws Exception {
        FDHolder holder = FDHolder.builder().name("Alice Smith").relationship("DAUGHTER").isSeniorCitizen(false).build();

        mockMvc.perform(post("/api/fixed-deposits/holders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(holder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Alice Smith")))
                .andExpect(jsonPath("$.relationship", is("DAUGHTER")));
    }
}
