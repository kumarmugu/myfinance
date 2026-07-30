package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "net_worth_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetWorthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private BigDecimal totalEquity;

    @Column(nullable = false)
    private BigDecimal totalIndexFund;

    @Column(nullable = false)
    private BigDecimal totalMutualFund;

    @Column(nullable = false)
    private BigDecimal totalCrypto;

    @Column(nullable = false)
    private BigDecimal totalBankDeposit;

    @Column(nullable = false)
    private BigDecimal totalNetWorth;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
