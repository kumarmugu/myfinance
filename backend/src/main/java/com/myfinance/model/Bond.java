package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bonds")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bond {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String name; // "Singapore Savings Bond Oct 2025", "Temasek 5Y"

    private String issuer; // MAS, Temasek, corporate name, etc.

    private String bondType; // GOVERNMENT, CORPORATE, MUNICIPAL, TREASURY, SAVINGS, OTHER

    private String isin; // optional ISIN / instrument code

    /**
     * Original currency of the bond's monetary values (e.g. SGD, USD). Nullable for
     * backward compatibility — null is treated as the user's base currency. This is the
     * source of truth; base/display values are always derived via FX, never persisted.
     */
    @Column(length = 10)
    private String currency;

    @Column(precision = 18, scale = 2)
    private BigDecimal faceValue; // par / nominal value

    @Column(precision = 18, scale = 2)
    private BigDecimal purchasePrice; // total amount paid

    @Column(precision = 18, scale = 2)
    private BigDecimal currentValue; // current market value

    @Column(precision = 8, scale = 4)
    private BigDecimal couponRate; // annual coupon %, e.g. 3.5000

    private String couponFrequency; // ANNUAL, SEMI_ANNUAL, QUARTERLY, MONTHLY, ZERO_COUPON

    private LocalDate purchaseDate;

    private LocalDate maturityDate;

    @Builder.Default
    private String status = "HELD"; // HELD, MATURED, SOLD

    @Builder.Default
    private Boolean includeInNetWorth = true;

    private String notes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
