package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "currency_rates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CurrencyRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Column(nullable = false, length = 10)
    private String fromCurrency;

    @Column(nullable = false, length = 10)
    private String toCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    /**
     * Broker/exchange spread as a percentage below the mid-market {@link #rate}, applied when
     * converting this currency into the user's base for consolidation (Net Worth, summaries).
     * Brokers pay less than mid-market when you repatriate, so the effective conversion rate is
     * {@code rate * (1 - spreadPct/100)}. Null or 0 = use the mid-market rate as-is.
     * e.g. rate 1.35 with spreadPct 1.5 → effective 1.32975.
     */
    @Column(precision = 6, scale = 3)
    private BigDecimal spreadPct;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
