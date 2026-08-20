package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private HomeLoan loan;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false)
    private BigDecimal amount; // total payment

    private BigDecimal principalPortion;

    private BigDecimal interestPortion;

    private BigDecimal balanceAfter; // outstanding after payment

    private String paymentType; // REGULAR, LUMP_SUM, PREPAYMENT

    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
