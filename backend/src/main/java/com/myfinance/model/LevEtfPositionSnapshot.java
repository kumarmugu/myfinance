package com.myfinance.model;

import com.myfinance.model.enums.levetf.PositionSource;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A recorded leveraged-ETF position for a strategy (manual, import, or broker sync). {@code externalId}
 * supports idempotent re-import/re-sync so the same position is never double-counted.
 *
 * <p>Additive table {@code lev_etf_positions}; new user-owned entity with {@code userId}; money
 * fields {@link BigDecimal}.
 */
@Entity
@Table(name = "lev_etf_positions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LevEtfPositionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long strategyId;
    private Long etfInstrumentId;

    private LocalDate snapshotDate;

    private BigDecimal quantity;
    private BigDecimal averageCost;
    private BigDecimal marketPrice;
    private BigDecimal marketValue;

    private String tradingCurrency;
    private BigDecimal fxRate;
    private BigDecimal baseCurrencyValue;

    @Enumerated(EnumType.STRING)
    private PositionSource source;

    /** Stable id for dedupe on re-import/re-sync (e.g. broker exec id or synthetic file id). */
    @Column(length = 64)
    private String externalId;

    private LocalDateTime syncTimestamp;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
