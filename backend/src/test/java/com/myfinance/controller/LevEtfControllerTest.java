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
    @Autowired private com.myfinance.repository.LevEtfAlertPrefRepository alertPrefRepository;
    @Autowired private com.myfinance.repository.LevEtfAlertHistoryRepository alertHistoryRepository;
    @Autowired private com.myfinance.repository.LevEtfPositionSnapshotRepository positionRepository;
    @Autowired private com.myfinance.repository.AllocationSnapshotRepository allocationSnapshotRepository;
    @Autowired private com.myfinance.repository.RebalancePlanRepository rebalancePlanRepository;
    @Autowired private com.myfinance.repository.LevEtfBacktestRepository backtestRepository;
    @Autowired private com.myfinance.repository.LevEtfInstrumentRepository instrumentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setup() {
        alertHistoryRepository.deleteAll();
        alertPrefRepository.deleteAll();
        rebalancePlanRepository.deleteAll();
        allocationSnapshotRepository.deleteAll();
        positionRepository.deleteAll();
        backtestRepository.deleteAll();
        barRepository.deleteAll();
        strategyRepository.deleteAll();
        instrumentRepository.deleteAll();
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

    // ─────────── CRUD coverage ───────────

    @Test
    @WithMockUser(username = "levUserA")
    void benchmarkCrud_roundTrips() throws Exception {
        String created = mockMvc.perform(post("/api/lev-etf/benchmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"^NDX\",\"name\":\"Nasdaq 100\",\"currency\":\"USD\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", is("^NDX")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/lev-etf/benchmarks")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(put("/api/lev-etf/benchmarks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"^NDX\",\"name\":\"NDX renamed\",\"currency\":\"USD\",\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", is("NDX renamed")));

        mockMvc.perform(delete("/api/lev-etf/benchmarks/" + id)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void instrumentCrud_roundTrips() throws Exception {
        String created = mockMvc.perform(post("/api/lev-etf/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TQQQ\",\"name\":\"3x Nasdaq\",\"leverageMultiple\":3,\"tradingCurrency\":\"USD\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", is("TQQQ")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/lev-etf/instruments")).andExpect(status().isOk());
        mockMvc.perform(put("/api/lev-etf/instruments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TQQQ\",\"name\":\"3x Nasdaq (edit)\",\"leverageMultiple\":3,\"tradingCurrency\":\"USD\",\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", is("3x Nasdaq (edit)")));
        mockMvc.perform(delete("/api/lev-etf/instruments/" + id)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void strategyCreateUpdate_bumpsRuleVersionOnRuleChange() throws Exception {
        String created = mockMvc.perform(post("/api/lev-etf/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wizard S\",\"initialAllocationPercent\":10,\"drawdownMultiplier\":0.5," +
                                "\"minimumAllocationPercent\":10,\"maximumAllocationPercent\":50,\"maximumAllocationEnabled\":true," +
                                "\"allocationMode\":\"INITIAL_PLUS_HALF_DRAWDOWN\",\"referenceHighMode\":\"ALL_TIME\"," +
                                "\"portfolioScope\":\"MANUAL\",\"scopeManualValue\":100000,\"rebalanceTolerancePercent\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleVersion", is(1)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/lev-etf/strategies/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wizard S\",\"initialAllocationPercent\":10,\"drawdownMultiplier\":1.0," +
                                "\"minimumAllocationPercent\":10,\"maximumAllocationPercent\":50,\"maximumAllocationEnabled\":true," +
                                "\"allocationMode\":\"INITIAL_PLUS_HALF_DRAWDOWN\",\"referenceHighMode\":\"ALL_TIME\"," +
                                "\"portfolioScope\":\"MANUAL\",\"scopeManualValue\":100000,\"rebalanceTolerancePercent\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion", is(2)));

        mockMvc.perform(delete("/api/lev-etf/strategies/" + id)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void positions_addListDelete_andDeriveBaseValue() throws Exception {
        LevEtfStrategy s = strategyFor(userA, null);
        String created = mockMvc.perform(post("/api/lev-etf/strategies/" + s.getId() + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10,\"marketPrice\":50,\"marketValue\":500,\"tradingCurrency\":\"SGD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseCurrencyValue", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        Long pid = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(get("/api/lev-etf/strategies/" + s.getId() + "/positions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(delete("/api/lev-etf/positions/" + pid)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void rebalancePlan_blocksWithoutEtfPrice() throws Exception {
        BenchmarkIndex bm = benchmarkFor(userA);
        LevEtfStrategy s = strategyFor(userA, bm.getId());
        s.setPortfolioScope(com.myfinance.model.enums.levetf.PortfolioScopeType.MANUAL);
        s.setScopeManualValue(new BigDecimal("100000"));
        strategyRepository.save(s);
        mockMvc.perform(post("/api/lev-etf/market-data/BENCHMARK/" + bm.getId() + "/manual-bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-01-15\",\"close\":60,\"currency\":\"USD\"}")).andExpect(status().isOk());

        // Without an ETF price the rebalance is blocked → generate 400 (no fabricated trade).
        mockMvc.perform(post("/api/lev-etf/strategies/" + s.getId() + "/rebalance-plan"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/lev-etf/rebalance-plans").param("strategyId", s.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void alertPrefs_and_notifications_crud() throws Exception {
        String created = mockMvc.perform(post("/api/lev-etf/alert-prefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"DRAWDOWN_THRESHOLD\",\"threshold\":30,\"inAppEnabled\":true,\"emailEnabled\":false}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(get("/api/lev-etf/alert-prefs")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/lev-etf/notifications")).andExpect(status().isOk());
        mockMvc.perform(get("/api/lev-etf/notifications/unread-count"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count", notNullValue()));
        mockMvc.perform(post("/api/lev-etf/notifications/read-all")).andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/lev-etf/alert-prefs/" + id)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "levUserA")
    void calculate_firesDrawdownAlert_whenThresholdMet() throws Exception {
        BenchmarkIndex bm = benchmarkFor(userA);
        LevEtfStrategy s = strategyFor(userA, bm.getId());
        mockMvc.perform(post("/api/lev-etf/alert-prefs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategyId\":" + s.getId() + ",\"trigger\":\"DRAWDOWN_THRESHOLD\",\"threshold\":30,\"inAppEnabled\":true,\"emailEnabled\":false}"))
                .andExpect(status().isCreated());
        // Bar at 60 vs ref high 100 → 40% drawdown ≥ 30 threshold → alert fires.
        mockMvc.perform(post("/api/lev-etf/market-data/BENCHMARK/" + bm.getId() + "/manual-bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-01-15\",\"close\":60,\"currency\":\"USD\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/lev-etf/strategies/" + s.getId() + "/calculate")).andExpect(status().isOk());

        mockMvc.perform(get("/api/lev-etf/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "levUserA")
    void backtest_blocksWithoutHistory_andListsRuns() throws Exception {
        LevEtfStrategy s = strategyFor(userA, null);
        String body = "{\"strategyId\":" + s.getId() + ",\"initialPortfolioValue\":100000,\"rebalanceFrequency\":\"MONTHLY\"}";
        mockMvc.perform(post("/api/lev-etf/backtests")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.warnings", not(emptyOrNullString())));
        mockMvc.perform(get("/api/lev-etf/backtests")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "levUserA")
    void backtest_runsOverManualHistory() throws Exception {
        BenchmarkIndex bm = benchmarkFor(userA);
        String etfCreated = mockMvc.perform(post("/api/lev-etf/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TQQQ\",\"name\":\"3x\",\"leverageMultiple\":3,\"tradingCurrency\":\"USD\",\"enabled\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long etfId = objectMapper.readTree(etfCreated).get("id").asLong();
        LevEtfStrategy s = strategyFor(userA, bm.getId());
        s.setEtfInstrumentId(etfId);
        strategyRepository.save(s);

        for (String d : new String[]{"2026-01-05", "2026-01-06", "2026-01-07"}) {
            mockMvc.perform(post("/api/lev-etf/market-data/BENCHMARK/" + bm.getId() + "/manual-bar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"date\":\"" + d + "\",\"close\":90,\"currency\":\"USD\"}")).andExpect(status().isOk());
            mockMvc.perform(post("/api/lev-etf/market-data/ETF/" + etfId + "/manual-bar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"date\":\"" + d + "\",\"close\":25,\"currency\":\"USD\"}")).andExpect(status().isOk());
        }
        String body = "{\"strategyId\":" + s.getId() + ",\"benchmarkIndexId\":" + bm.getId()
                + ",\"etfInstrumentId\":" + etfId + ",\"initialPortfolioValue\":100000,\"rebalanceFrequency\":\"DAILY\"}";
        String run = mockMvc.perform(post("/api/lev-etf/backtests")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")))
                .andReturn().getResponse().getContentAsString();
        Long btId = objectMapper.readTree(run).get("id").asLong();
        mockMvc.perform(get("/api/lev-etf/backtests/" + btId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsJson", not(emptyOrNullString())));
    }

    @Test
    @WithMockUser(username = "levUserA")
    void marketDataHistory_readable_afterManualBar() throws Exception {
        BenchmarkIndex bm = benchmarkFor(userA);
        mockMvc.perform(post("/api/lev-etf/market-data/BENCHMARK/" + bm.getId() + "/manual-bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-01-10\",\"close\":123.45,\"currency\":\"USD\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/lev-etf/market-data/BENCHMARK/" + bm.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }
}
