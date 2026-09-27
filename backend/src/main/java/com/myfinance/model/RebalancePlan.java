package com.myfinance.model;

import com.myfinance.model.enums.levetf.RebalanceAction;
import com.myfinance.model.enums.levetf.RebalancePlanStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A rebalance proposal and its decision trail. Advisory only: approval requires explicit user
 * confirmation and NEVER places a broker order; execution is recorded manually. Stores the exact
 * {@code allocationSnapshotId} + {@code ruleVersion} used, so later rule edits don't change it.
 *
 * <p>Additive table {@code lev_etf_rebalance_plans}; new user-owned entity with {@code userId}; money
 * fields {@link BigDecimal}; {@code @Version} for concurrent approve/execute safety.
 */
@Entity
@Table(name = "lev_etf_rebalance_plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RebalancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long strategyId;
    private Long allocationSnapshotId;
    private Integer ruleVersion;

    @Enumerated(EnumType.STRING)
    private RebalanceAction action;

    private BigDecimal quantity;
    private BigDecimal estimatedPrice;
    private BigDecimal estimatedAmount;
    private String currency;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RebalancePlanStatus status = RebalancePlanStatus.PENDING_REVIEW;

    private LocalDateTime approvalTimestamp;
    private LocalDateTime executionTimestamp;

    private BigDecimal executedQuantity;
    private BigDecimal executedPrice;
    private BigDecimal executedFees;
    private String brokerReference;

    @Column(length = 1000)
    private String notes;

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
