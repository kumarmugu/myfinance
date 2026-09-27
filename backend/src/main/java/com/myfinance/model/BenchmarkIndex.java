package com.myfinance.model;

import com.myfinance.model.enums.levetf.BenchmarkType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A benchmark index (or an ETF used as a proxy for one) whose drawdown drives a leveraged-ETF
 * strategy. Distinct instrument from the traded ETF — its own price series. Per-user (tenant) owned.
 *
 * <p>Additive table {@code lev_etf_benchmarks}; new user-owned entity with {@code userId}. No money
 * fields (a benchmark carries no monetary amount here — prices live in {@code MarketDataBar}).
 */
@Entity
@Table(name = "lev_etf_benchmarks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BenchmarkIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    private String exchange;
    private String provider;
    private String providerInstrumentId;
    private String currency;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BenchmarkType benchmarkType = BenchmarkType.PRICE_INDEX;

    private String timezone;

    @Builder.Default
    private Boolean enabled = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
