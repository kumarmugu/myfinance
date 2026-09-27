package com.myfinance.model;

import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.InvestmentPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
// A holding's identity is asset + account + owner + PURPOSE: the same symbol bought with cash vs SRS
// money must live in two separate rows. That's enforced at the application layer (see
// HoldingRepository.findByPosition / TransactionService.updateHolding), which is the sole writer of
// holdings. We intentionally declare no DB-level unique constraint here: the old
// (asset_id, account_id, owner_id) constraint would reject a second, differently-purposed position,
// and ddl-auto=update cannot safely swap a composite unique constraint on the existing prod database.
@Table(name = "holdings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Holding {

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

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal averageBuyPrice;

    @Column(nullable = false)
    private BigDecimal investedAmount;

    /**
     * Quantity-weighted average of the trade→account(base) FX rate across the BUYs that make up
     * this holding (from each buy's Transaction.fxRateToBase). Lets a later SELL value the cost
     * basis at the FX rate actually paid, so realized P/L separates the stock move from the FX
     * move. Nullable: same-currency holdings (trade ccy == account ccy) leave this null → treated
     * as rate 1. Legacy rows are null until the next buy refreshes them.
     */
    private BigDecimal averageBuyFxRate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.USD;

    @Enumerated(EnumType.STRING)
    private InvestmentPurpose purpose;

    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
