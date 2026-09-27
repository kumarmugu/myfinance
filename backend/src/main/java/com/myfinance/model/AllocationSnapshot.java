package com.myfinance.model;

import com.myfinance.model.enums.levetf.DataQuality;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An immutable record of one allocation calculation for a strategy: the inputs (benchmark price,
 * reference high, portfolio value, FX), the outputs (drawdown, target/actual allocation, target/current
 * ETF value, rebalance difference), the {@code ruleVersion} used, and the input timestamps + data
 * quality. Never mutated after creation, so later rule edits cannot change history.
 *
 * <p>Additive table {@code lev_etf_allocation_snapshots}; new user-owned entity with {@code userId};
 * money/percent fields {@link BigDecimal}.
 */
@Entity
@Table(name = "lev_etf_allocation_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AllocationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long strategyId;
    private Integer ruleVersion;

    private BigDecimal benchmarkPrice;
    private BigDecimal referenceHigh;
    private LocalDate referenceHighDate;
    private BigDecimal drawdownPercent;
    private BigDecimal targetAllocationPercent;
    private BigDecimal actualAllocationPercent;
    private BigDecimal portfolioValueBase;
    private BigDecimal targetEtfValueBase;
    private BigDecimal currentEtfValueBase;
    private BigDecimal rebalanceDifferenceBase;
    private BigDecimal fxRate;

    private LocalDateTime calculationTimestamp;
    private LocalDateTime benchmarkPriceTimestamp;
    private LocalDateTime etfPriceTimestamp;

    @Enumerated(EnumType.STRING)
    private DataQuality dataQuality;

    /** Non-null when the calculation was blocked (stale/missing/invalid inputs); then figures are null. */
    private String blockedReason;

    @PrePersist
    protected void onCreate() { if (calculationTimestamp == null) calculationTimestamp = LocalDateTime.now(); }
}
