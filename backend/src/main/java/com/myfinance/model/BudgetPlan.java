package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Monthly budget plan header. One per user per month.
 */
@Entity
@Table(name = "budget_plans", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "\"year\"", "\"month\""}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BudgetPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, name = "\"year\"")
    private Integer year;

    @Column(nullable = false, name = "\"month\"")
    private Integer month; // 1-12

    /** Savings target percentage (0-100). Default 50. */
    @Builder.Default
    @Column(precision = 5, scale = 2)
    private BigDecimal savingsTargetPct = new BigDecimal("50.00");

    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
