package com.myfinance.service.levetf;

import com.myfinance.model.*;
import com.myfinance.model.enums.levetf.*;
import com.myfinance.repository.*;
import com.myfinance.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the advisory rebalance workflow end-to-end at the service layer: generate a proposal from a
 * fresh snapshot, then approve → record execution → and separately reject/cancel. Confirms plans pin the
 * snapshot's ruleVersion and that no order is ever placed (execution is user-recorded).
 */
@SpringBootTest
class RebalancePlanServiceTest {

    @Autowired private RebalancePlanService rebalanceService;
    @Autowired private LevEtfPlannerService plannerService;
    @Autowired private TenantContext tenantContext;
    @Autowired private AppUserRepository userRepository;
    @Autowired private BenchmarkIndexRepository benchmarkRepository;
    @Autowired private LevEtfInstrumentRepository instrumentRepository;
    @Autowired private LevEtfStrategyRepository strategyRepository;
    @Autowired private LevEtfPositionSnapshotRepository positionRepository;
    @Autowired private MarketDataBarRepository barRepository;
    @Autowired private RebalancePlanRepository planRepository;
    @Autowired private AllocationSnapshotRepository snapshotRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long userId;
    private Long strategyId;

    @BeforeEach
    void setup() {
        planRepository.deleteAll();
        snapshotRepository.deleteAll();
        positionRepository.deleteAll();
        barRepository.deleteAll();
        strategyRepository.deleteAll();
        instrumentRepository.deleteAll();
        benchmarkRepository.deleteAll();

        AppUser u = userRepository.findByUsername("rbUser").orElseGet(() ->
                userRepository.save(AppUser.builder().username("rbUser").email("rb@test.com")
                        .password(passwordEncoder.encode("pass")).displayName("RB").role("USER").build()));
        userId = u.getId();

        BenchmarkIndex bm = benchmarkRepository.save(BenchmarkIndex.builder()
                .userId(userId).symbol("^IDX").name("Index").currency("USD").enabled(true).build());
        LevEtfInstrument etf = instrumentRepository.save(LevEtfInstrument.builder()
                .userId(userId).symbol("TQQQ").name("3x").tradingCurrency("SGD").enabled(true).build());

        LevEtfStrategy s = strategyRepository.save(LevEtfStrategy.builder()
                .userId(userId).name("RB S").benchmarkIndexId(bm.getId()).etfInstrumentId(etf.getId())
                .initialAllocationPercent(new BigDecimal("10")).drawdownMultiplier(new BigDecimal("0.5"))
                .minimumAllocationPercent(new BigDecimal("10")).maximumAllocationPercent(new BigDecimal("50"))
                .maximumAllocationEnabled(true).allocationMode(AllocationMode.INITIAL_PLUS_HALF_DRAWDOWN)
                .referenceHighMode(ReferenceHighMode.MANUAL).referenceHighValue(new BigDecimal("100"))
                .portfolioScope(PortfolioScopeType.MANUAL).scopeManualValue(new BigDecimal("100000"))
                .rebalanceTolerancePercent(new BigDecimal("1")).tradingCurrency("SGD")
                .ruleVersion(1).archived(false).build());
        strategyId = s.getId();

        // Benchmark bar (60 vs high 100 → 40% drawdown → 20% target) and an ETF price so rebalance forms.
        barRepository.save(MarketDataBar.builder().userId(userId).instrumentType(InstrumentType.BENCHMARK)
                .instrumentId(bm.getId()).date(LocalDate.of(2026, 1, 15)).close(new BigDecimal("60"))
                .currency("USD").provider("MANUAL").sourceTimestamp(java.time.LocalDateTime.now())
                .dataQuality(DataQuality.OK).build());
        barRepository.save(MarketDataBar.builder().userId(userId).instrumentType(InstrumentType.ETF)
                .instrumentId(etf.getId()).date(LocalDate.of(2026, 1, 15)).close(new BigDecimal("25"))
                .currency("SGD").provider("MANUAL").sourceTimestamp(java.time.LocalDateTime.now())
                .dataQuality(DataQuality.OK).build());
        // A small existing position so current ETF value < target (100000 * 20% = 20000) → BUY.
        positionRepository.save(LevEtfPositionSnapshot.builder().userId(userId).strategyId(strategyId)
                .etfInstrumentId(etf.getId()).quantity(new BigDecimal("10")).marketValue(new BigDecimal("250"))
                .baseCurrencyValue(new BigDecimal("250")).tradingCurrency("SGD").source(PositionSource.MANUAL).build());
    }

    @Test
    @WithMockUser(username = "rbUser")
    void generate_thenApprove_thenRecordExecution() {
        RebalancePlan plan = rebalanceService.generate(userId, strategyId);
        assertThat(plan.getStatus()).isEqualTo(RebalancePlanStatus.PENDING_REVIEW);
        assertThat(plan.getAction()).isEqualTo(RebalanceAction.BUY);
        assertThat(plan.getRuleVersion()).isEqualTo(1);
        assertThat(plan.getAllocationSnapshotId()).isNotNull();

        RebalancePlan approved = rebalanceService.approve(userId, plan.getId());
        assertThat(approved.getStatus()).isEqualTo(RebalancePlanStatus.APPROVED);
        assertThat(approved.getApprovalTimestamp()).isNotNull();

        RebalancePlan executed = rebalanceService.recordExecution(userId, plan.getId(),
                new BigDecimal("100"), new BigDecimal("25"), new BigDecimal("1"), "BRK-1", false, "done");
        assertThat(executed.getStatus()).isEqualTo(RebalancePlanStatus.EXECUTED);
        assertThat(executed.getExecutedQuantity()).isEqualByComparingTo("100");
        assertThat(executed.getBrokerReference()).isEqualTo("BRK-1");
    }

    @Test
    @WithMockUser(username = "rbUser")
    void partialExecution_setsPartiallyExecuted() {
        RebalancePlan plan = rebalanceService.generate(userId, strategyId);
        rebalanceService.approve(userId, plan.getId());
        RebalancePlan partial = rebalanceService.recordExecution(userId, plan.getId(),
                new BigDecimal("50"), new BigDecimal("25"), BigDecimal.ZERO, null, true, null);
        assertThat(partial.getStatus()).isEqualTo(RebalancePlanStatus.PARTIALLY_EXECUTED);
    }

    @Test
    @WithMockUser(username = "rbUser")
    void reject_marksCancelled() {
        RebalancePlan plan = rebalanceService.generate(userId, strategyId);
        RebalancePlan rejected = rebalanceService.reject(userId, plan.getId(), "not now");
        assertThat(rejected.getStatus()).isEqualTo(RebalancePlanStatus.CANCELLED);
        assertThat(rejected.getNotes()).isEqualTo("not now");
    }

    @Test
    @WithMockUser(username = "rbUser")
    void cancel_marksCancelled() {
        RebalancePlan plan = rebalanceService.generate(userId, strategyId);
        rebalanceService.cancel(userId, plan.getId());
        assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
                .isEqualTo(RebalancePlanStatus.CANCELLED);
    }

    @Test
    @WithMockUser(username = "rbUser")
    void cannotExecute_beforeApproval() {
        RebalancePlan plan = rebalanceService.generate(userId, strategyId);
        assertThatThrownBy(() -> rebalanceService.recordExecution(userId, plan.getId(),
                BigDecimal.ONE, BigDecimal.ONE, null, null, false, null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @WithMockUser(username = "rbUser")
    void list_returnsPlansForStrategy() {
        rebalanceService.generate(userId, strategyId);
        assertThat(rebalanceService.list(userId, strategyId)).isNotEmpty();
        assertThat(rebalanceService.list(userId, null)).isNotEmpty();
    }
}
