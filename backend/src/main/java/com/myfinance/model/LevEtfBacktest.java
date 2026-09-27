package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A backtest run: its configuration (JSON), status, results (JSON), and explicit warnings. Uses the
 * ETF's own historical price series (never a fabricated multiple-of-index). Kept per-user.
 *
 * <p>Additive table {@code lev_etf_backtests}; new user-owned entity with {@code userId}; the initial
 * portfolio value is {@link BigDecimal}. Large config/results stored as JSON text (H2/Postgres safe).
 */
@Entity
@Table(name = "lev_etf_backtests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LevEtfBacktest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long strategyId;
    private Integer ruleVersion;

    private Long benchmarkIndexId;
    private Long etfInstrumentId;

    private BigDecimal initialPortfolioValue;
    private LocalDate startDate;
    private LocalDate endDate;
    private String rebalanceFrequency;

    /** Full input configuration (fees/slippage/fx/cash/reference-high method, etc.). */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String configJson;

    /** "QUEUED", "RUNNING", "DONE", "FAILED". */
    @Builder.Default
    private String status = "QUEUED";

    /** Computed metrics + series. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultsJson;

    /** Explicit caveats (incomplete data, omitted costs, not a prediction). */
    @Column(length = 2000)
    private String warnings;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
