package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Record of a stock split the user has applied to a symbol. Persisted so a split is <em>idempotent</em>:
 * re-importing a broker file that still lists the same split event won't apply it a second time (which
 * would wrongly re-scale the holding again). One row per userId + symbol + effectiveDate.
 *
 * <p>Additive/prod-safe: a brand-new table with a {@code userId} tenant column. The ratio is stored as
 * two integer counts (numerator:denominator) — these are share counts, not money, so no BigDecimal.
 */
@Entity
@Table(name = "stock_splits", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "symbol", "effective_date"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private Integer numerator;

    @Column(nullable = false)
    private Integer denominator;

    @Column(updatable = false)
    private LocalDateTime appliedAt;

    @PrePersist
    protected void onCreate() { appliedAt = LocalDateTime.now(); }
}
