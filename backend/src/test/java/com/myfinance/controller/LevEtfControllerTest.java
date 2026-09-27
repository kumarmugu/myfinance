package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.AppUser;
import com.myfinance.model.BenchmarkIndex;
import com.myfinance.model.LevEtfStrategy;
import com.myfinance.model.enums.levetf.AllocationMode;
import com.myfinance.model.enums.levetf.ReferenceHighMode;
import com.myfinance.repository.AppUserRepository;
import com.myfinance.repository.BenchmarkIndexRepository;
import com.myfinance.repository.LevEtfStrategyRepository;
import com.myfinance.repository.MarketDataBarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration + security tests for the Leveraged ETF Planner API. Covers tenant isolation (User A must
 * never see or act on User B's strategy), the end-to-end calculate flow producing the mandated
 * drawdown→allocation result from a manually-entered benchmark bar, and blocking when data is missing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LevEtfControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private LevEtfStrategyRepository strategyRepository;
    @Autowired private BenchmarkIndexRepository benchmarkRepository;
    @Autowired private MarketDataBarRepository barRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setup() {
        barRepository.deleteAll();
        strategyRepository.deleteAll();
        benchmarkRepository.deleteAll();

        userA = user("levUserA", "leva@test.com");
        userB = user("levUserB", "levb@test.com");
    }

    private AppUser user(String username, String email) {
        return appUserRepository.findByUsername(username).orElseGet(() ->
                appUserRepository.save(AppUser.builder()
                        .username(username).email(email)
                        .password(passwordEncoder.encode("pass"))
                        .displayName(username).role("USER").build()));
    }

    private LevEtfStrategy strategyFor(AppUser owner, Long benchmarkId) {
        return strategyRepository.save(LevEtfStrategy.builder()
                .userId(owner.getId())
                .name("S-" + owner.getUsername())
                .benchmarkIndexId(benchmarkId)
                .initialAllocationPercent(new BigDecimal("10"))
                .drawdownMultiplier(new BigDecimal("0.5"))
                .minimumAllocationPercent(new BigDecimal("10"))
                .maximumAllocationPercent(new BigDecimal("50"))
                .maximumAllocationEnabled(true)
                .allocationMode(AllocationMode.INITIAL_PLUS_HALF_DRAWDOWN)
                .referenceHighMode(ReferenceHighMode.MANUAL)
                .referenceHighValue(new BigDecimal("100"))
                .rebalanceTolerancePercent(new BigDecimal("1"))
                .ruleVersion(1)
                .archived(false)
                .build());
    }

    private BenchmarkIndex benchmarkFor(AppUser owner) {
        return benchmarkRepository.save(BenchmarkIndex.builder()
                .userId(owner.getId()).symbol("^IDX").name("Index").currency("USD").enabled(true).build());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void listStrategies_isTenantScoped() throws Exception {
        strategyFor(userA, null);
        strategyFor(userB, null);
        mockMvc.perform(get("/api/lev-etf/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("S-levUserA")));
    }

    @Test
    @WithMockUser(username = "levUserA")
    void cannotReadAnotherUsersStrategy() throws Exception {
        LevEtfStrategy bStrategy = strategyFor(userB, null);
        // GlobalExceptionHandler maps the not-found RuntimeException to 400 with a message.
        mockMvc.perform(get("/api/lev-etf/strategies/" + bStrategy.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    @WithMockUser(username = "levUserA")
    void cannotCalculateAnotherUsersStrategy() throws Exception {
        LevEtfStrategy bStrategy = strategyFor(userB, null);
        mockMvc.perform(post("/api/lev-etf/strategies/" + bStrategy.getId() + "/calculate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    @WithMockUser(username = "levUserA")
    void calculate_blocksWhenNoBenchmarkHistory() throws Exception {
        BenchmarkIndex bm = benchmarkFor(userA);
        LevEtfStrategy s = strategyFor(userA, bm.getId());
        mockMvc.perform(post("/api/lev-etf/strategies/" + s.getId() + "/calculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedReason", not(emptyOrNullString())))
                .andExpect(jsonPath("$.targetAllocationPercent").doesNotExist());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void calculate_producesMandatedAllocation_fromManualBar() throws Exception {
        BenchmarkIndex bm = benchmarkFor(userA);
        LevEtfStrategy s = strategyFor(userA, bm.getId());

        // Manual benchmark close of 60 with a reference high of 100 → 40% drawdown → target 20%.
        // INITIAL_PLUS_HALF_DRAWDOWN: raw = 40 * 0.5 = 20; max(20, initial 10) = 20; under the 50 cap.
        String barBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("date", "2026-01-15");
            put("close", 60);
            put("currency", "USD");
        }});
        mockMvc.perform(post("/api/lev-etf/market-data/BENCHMARK/" + bm.getId() + "/manual-bar")
                        .contentType(MediaType.APPLICATION_JSON).content(barBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/lev-etf/strategies/" + s.getId() + "/calculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drawdownPercent", closeTo(40.0, 0.001)))
                .andExpect(jsonPath("$.targetAllocationPercent", closeTo(20.0, 0.001)));
    }

    @Test
    @WithMockUser(username = "levUserA")
    void allocationCurvePreview_returnsFullRange() throws Exception {
        LevEtfStrategy s = strategyFor(userA, null);
        mockMvc.perform(post("/api/lev-etf/preview/allocation-curve")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(21))) // 0..100 step 5
                .andExpect(jsonPath("$[0].drawdownPercent", is(0)));
    }
}
