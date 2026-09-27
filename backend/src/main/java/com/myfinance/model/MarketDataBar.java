package com.myfinance.model;

import com.myfinance.model.enums.levetf.DataQuality;
import com.myfinance.model.enums.levetf.InstrumentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One daily OHLCV bar for a benchmark or ETF, per user. Persisted so drawdown, reference high, and
 * backtests have history. {@code dataQuality} flags STALE/INVALID/MISSING so the engine can exclude
 * bad data (never zero-substituted).
 *
 * <p>Additive table {@code lev_etf_market_bars}; new user-owned entity with {@code userId}. Prices are
 * {@link BigDecimal}. Unique on (userId, instrumentType, instrumentId, date) so refresh is idempotent.
 */
@Entity
@Table(name = "lev_etf_market_bars", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "instrument_type", "instrument_id", "bar_date"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MarketDataBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false)
    private InstrumentType instrumentType;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "bar_date", nullable = false)
    private LocalDate date;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal adjustedClose;
    private BigDecimal volume;

    private String currency;
    private String provider;
    private LocalDateTime sourceTimestamp;
    private LocalDateTime ingestionTimestamp;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DataQuality dataQuality = DataQuality.OK;

    @PrePersist
    protected void onCreate() { if (ingestionTimestamp == null) ingestionTimestamp = LocalDateTime.now(); }
}
