package com.myfinance.model;

import com.myfinance.model.enums.AccountType;
import com.myfinance.model.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.SGD;

    private String accountNumber;

    private String description;

    /**
     * Uninvested cash sitting in this broker/exchange account, in the account's {@link #currency}.
     * Nullable/zero = no cash. This is the source of truth (the user maintains it); base/display
     * values are always derived via FX. Counted in Net Worth as the CASH module when
     * {@link #includeCashInNetWorth} is true and the CASH config toggle is enabled.
     */
    @Column(precision = 18, scale = 2)
    private BigDecimal cashBalance;

    /** Whether this account's cash balance is included in Net Worth. Defaults to true. */
    @Builder.Default
    private Boolean includeCashInNetWorth = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
