package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "generic_fixed_deposits")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GenericFixedDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String bankName;

    private String accountNumber;

    @Column(nullable = false)
    private BigDecimal principalAmount;

    @Column(nullable = false)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate maturityDate;

    private String tenure; // e.g. "12 months", "6 months"

    private BigDecimal expectedInterest;

    private String currency; // SGD, USD, etc.

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, MATURED, CLOSED

    @Builder.Default
    private Boolean includeInNetWorth = true;

    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
