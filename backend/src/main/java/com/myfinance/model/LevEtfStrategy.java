package com.myfinance.model;

import com.myfinance.model.enums.levetf.AllocationMode;
import com.myfinance.model.enums.levetf.PortfolioScopeType;
import com.myfinance.model.enums.levetf.ReferenceHighMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A user's leveraged-ETF allocation strategy: which benchmark drives it, which ETF it targets, the
 * allocation rule + caps, the reference-high method, portfolio scope, and rebalance settings.
 *
 * <p>Additive table {@code lev_etf_strategies}; new user-owned entity with {@code userId}. Percent /
 * money fields are {@link BigDecimal}. {@code @Version} gives optimistic concurrency for concurrent
 * edits; {@code ruleVersion} is bumped on rule-affecting edits so historical snapshots/plans keep the
 * exact rule they were computed under.
 */
@Entity
@Table(name = "lev_etf_strategies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LevEtfStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String name;

    private String description;

    private Long benchmarkIndexId;
    private Long etfInstrumentId;

    @Builder.Default
    private BigDecimal initialAllocationPercent = new BigDecimal("10");
    @Builder.Default
    private BigDecimal drawdownMultiplier = new BigDecimal("0.5");
    @Builder.Default
    private BigDecimal minimumAllocationPercent = new BigDecimal("10");
    @Builder.Default
    private BigDecimal maximumAllocationPercent = new BigDecimal("50");
    @Builder.Default
    private Boolean maximumAllocationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AllocationMode allocationMode = AllocationMode.INITIAL_PLUS_HALF_DRAWDOWN;

    /** JSON array of {threshold,allocation} rows; used only when allocationMode = LADDER. */
    @Column(length = 4000)
    private String ladderJson;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReferenceHighMode referenceHighMode = ReferenceHighMode.ALL_TIME;

    private BigDecimal referenceHighValue;
    private LocalDate referenceHighDate;
    @Builder.Default
    private Boolean referenceHighFrozen = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PortfolioScopeType portfolioScope = PortfolioScopeType.WHOLE;
    private Long scopeAccountId;
    private BigDecimal scopeManualValue;

    @Builder.Default
    private BigDecimal rebalanceTolerancePercent = new BigDecimal("1");
    private String rebalanceFrequency;
    private String tradingCurrency;

    /** Bumped when a rule-affecting field changes; stamped onto snapshots/plans. */
    @Builder.Default
    private Integer ruleVersion = 1;

    @Builder.Default
    private Boolean enabled = true;
    @Builder.Default
    private Boolean archived = false;

    /** Optimistic-locking version for concurrent edits. */
    @Version
    private Long version;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
