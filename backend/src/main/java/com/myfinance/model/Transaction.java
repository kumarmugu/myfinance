package com.myfinance.model;

import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.InvestmentPurpose;
import com.myfinance.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal pricePerUnit;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Builder.Default
    private BigDecimal fees = BigDecimal.ZERO;

    /**
     * Currency of {@link #fees}. May differ from the trade currency — e.g. a Saxo trade priced
     * in USD but charged a fee in SGD. Nullable: when null, the fee is assumed to be in the
     * transaction's own {@link #currency} (backward-compatible for existing rows).
     */
    private String feeCurrency;

    /**
     * FX rate from the trade {@link #currency} into the settling broker account's currency,
     * captured AT PURCHASE. Used to lock in an exact cost basis for cross-currency buys
     * (e.g. USD-priced Tesla bought through an SGD Saxo account). Nullable: only set when the
     * trade currency differs from the account currency; otherwise no conversion is needed.
     */
    private BigDecimal fxRateToBase;

    /**
     * Realized profit/loss for a SELL, in the broker account's currency, computed at sale time as
     * proceeds − cost basis − fees. Null on BUYs and on legacy sells. See {@link #realizedStockPnl}
     * and {@link #realizedFxPnl} for the stock-move vs FX-move split (their sum equals this).
     */
    private BigDecimal realizedPnl;

    /** The stock-price component of {@link #realizedPnl} (valued at the buy-time FX rate). */
    private BigDecimal realizedStockPnl;

    /** The FX component of {@link #realizedPnl} (gain/loss from sell-time FX vs buy-time FX). */
    private BigDecimal realizedFxPnl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.USD;

    @Column(nullable = false)
    private LocalDate transactionDate;

    private String notes;

    @Enumerated(EnumType.STRING)
    private InvestmentPurpose purpose;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (totalAmount == null && quantity != null && pricePerUnit != null) {
            totalAmount = quantity.multiply(pricePerUnit);
        }
    }
}
