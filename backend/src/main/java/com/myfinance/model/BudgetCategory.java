package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User-configurable expense categories for budget planning.
 * Each user maintains their own set of categories.
 */
@Entity
@Table(name = "budget_categories", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BudgetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String name; // e.g. "Groceries", "Rent", "Entertainment"

    private String parentCategory; // e.g. "Essential", "Lifestyle", "Education", "Family", "Special"

    @Builder.Default
    private Integer sortOrder = 0;

    @Builder.Default
    private Boolean isActive = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
