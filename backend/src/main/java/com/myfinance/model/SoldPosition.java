package com.myfinance.model;

import com.myfinance.model.enums.Currency;
import com.myfinance.model.enums.InvestmentPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sold_positions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SoldPosition {

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
    private BigDecimal buyPrice;

    @Column(nullable = false)
    private BigDecimal sellPrice;

    @Column(nullable = false)
    private BigDecimal investedAmount;

    @Column(nullable = false)
    private BigDecimal soldAmount;

    @Column(nullable = false)
    private BigDecimal profit;

    private BigDecimal profitPercentage;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.USD;

    @Column(nullable = false)
    private LocalDate investedDate;

    @Column(nullable = false)
    private LocalDate soldDate;

    /**
     * The SELL Transaction this closed position was generated from, when it was created
     * automatically by selling on the Transactions page. Lets edits/deletes of that sell keep the
     * matching sold-position record in sync. Nullable: manually-created sold positions have none.
     */
    private Long sourceTransactionId;

    private String holdingPeriod;

    @Builder.Default
    private Boolean isShortTerm = false;

    @Enumerated(EnumType.STRING)
    private InvestmentPurpose purpose;

    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
