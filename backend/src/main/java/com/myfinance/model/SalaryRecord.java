package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Column(nullable = false, name = "\"year\"")
    private Integer year;

    @Column(nullable = false, name = "\"month\"")
    private Integer month; // 1-12

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private BigDecimal amount; // total take-home

    private BigDecimal basic;
    private BigDecimal allowance;
    private BigDecimal mobile;
    private BigDecimal support;
    private BigDecimal weekend;
    private BigDecimal mealAllowance;
    private BigDecimal deductions; // SINDA, etc.

    @Builder.Default
    private Boolean isBonus = false;

    private BigDecimal bonusMonths; // e.g. 4.25

    private String country; // Singapore, Sri Lanka

    /**
     * Original currency of this salary record (e.g. SGD, LKR). Nullable for
     * backward compatibility — existing rows are treated as the user's base
     * currency. The original amount is never overwritten by conversion.
     */
    @Column(length = 10)
    private String currency;

    private String notes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
