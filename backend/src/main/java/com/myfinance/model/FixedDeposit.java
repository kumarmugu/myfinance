package com.myfinance.model;

import com.myfinance.model.enums.FDStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fixed_deposits")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FixedDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "holder_id", nullable = false)
    private FDHolder holder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "joint_holder_id")
    private FDHolder jointHolder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    private String accountNumber;

    @Column(nullable = false)
    private BigDecimal principalAmount;

    @Column(nullable = false)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate maturityDate;

    private String period; // "12 Months", "7 Months", "400 days" etc.

    private String branch;

    private String category; // NORMAL, SENIOR_CITIZEN

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FDStatus status = FDStatus.ACTIVE;

    private BigDecimal expectedInterest;

    private String beneficiary; // APPA, AMMA, or specific name

    private String purpose; // deed, car, etc.

    private String notes;

    @Builder.Default
    private Boolean requiresUpdate = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
