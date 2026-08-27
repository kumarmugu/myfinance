package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Planned expense allocation per category within a budget plan.
 */
@Entity
@Table(name = "budget_allocations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BudgetAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "budget_plan_id", nullable = false)
    private BudgetPlan budgetPlan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private BudgetCategory category;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal plannedAmount;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
