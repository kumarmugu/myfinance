package com.myfinance.model;

import com.myfinance.model.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_savings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankSavings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    // Optional profile this account belongs to (Myself/Spouse/Son). Nullable so existing
    // accounts remain "Unlinked" until assigned. Additive column — safe under ddl-auto=update.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @Column(nullable = false)
    private String accountName; // e.g. "DBS Savings", "BOC Savings"

    private String bankName; // DBS, OCBC, BOC, NSB

    private String accountNumber;

    @Column(nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.SGD;

    private String country; // Singapore, Sri Lanka

    @Builder.Default
    private Boolean includeInNetWorth = true;

    private LocalDate lastUpdated;

    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
