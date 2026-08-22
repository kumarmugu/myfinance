package com.myfinance.model;

import com.myfinance.model.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "net_worth_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NetWorthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "snapshot_year")
    private Integer year;

    @Builder.Default
    private BigDecimal totalIndexFund = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalMutualFund = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalGrowthEquity = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalDividendEquity = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalLeveragedEtf = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalMoneyMarket = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalFixedDeposit = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalSavings = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalCrypto = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalNetWorth = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.SGD;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
