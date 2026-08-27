package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Planned income entry within a budget plan.
 * Multiple income sources per month (salary, bonus, dividends, etc.)
 */
@Entity
@Table(name = "budget_incomes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BudgetIncome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_plan_id", nullable = false)
    private BudgetPlan budgetPlan;

    @Column(nullable = false)
    private String source; // e.g. "Salary", "Bonus", "Dividend", "Interest", "Other"

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
