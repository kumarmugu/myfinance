package com.myfinance.model;

import com.myfinance.model.enums.AssetType;
import com.myfinance.model.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType assetType;

    private BigDecimal currentPrice;

    /**
     * When {@link #currentPrice} was last changed. Distinct from {@link #updatedAt} (which
     * changes on ANY edit) so the UI can show how fresh the price — and thus any P/L derived
     * from it — actually is. Nullable: legacy rows and assets whose price was never set have none.
     */
    private LocalDateTime priceUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.USD;

    private String exchange;
    private String description;

    /**
     * Comma-separated list of former tickers this instrument has traded under (e.g. META carries
     * {@code "FB"} after Facebook's rename). Lets a broker import that still reports the old ticker
     * fold into this (renamed) asset instead of creating a duplicate. Nullable: most assets have none.
     * Additive/prod-safe.
     */
    private String previousSymbols;

    @Builder.Default
    private Boolean includeInNetWorth = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
