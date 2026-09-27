package com.myfinance.model;

import com.myfinance.model.enums.levetf.LeverageDirection;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A leveraged ETF the user tracks. Distinct instrument from its underlying benchmark — its own price
 * series, and its price is NEVER used to compute the benchmark drawdown. Product characteristics are
 * explicit and never inferred; unknown fields are left null and {@code metadataSource} records origin.
 *
 * <p>Additive table {@code lev_etf_instruments}; new user-owned entity with {@code userId}. Money-like
 * ratio fields ({@code leverageMultiple}, {@code expenseRatio}) are {@link BigDecimal}.
 */
@Entity
@Table(name = "lev_etf_instruments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LevEtfInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    private String exchange;
    private String issuer;

    /** e.g. 2 or 3 (or 1.5). Explicit, never assumed. */
    private BigDecimal leverageMultiple;

    @Enumerated(EnumType.STRING)
    private LeverageDirection leverageDirection;

    /** FK-by-id to the benchmark this ETF tracks. */
    private Long underlyingBenchmarkId;

    private String tradingCurrency;

    /** Annual expense ratio as a fraction (e.g. 0.0095), if known; null when unknown. */
    private BigDecimal expenseRatio;

    /** e.g. "DAILY" for daily-reset products; null when unknown. Never assumed. */
    private String resetFrequency;

    private String dataProvider;
    private String providerInstrumentId;

    /** Where the metadata came from: "USER" or "PROVIDER". */
    private String metadataSource;

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
