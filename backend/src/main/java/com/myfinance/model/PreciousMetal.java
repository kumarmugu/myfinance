package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "precious_metals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PreciousMetal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String metalType; // GOLD, SILVER, PLATINUM

    @Column(nullable = false)
    private String form; // COIN, BAR, JEWELLERY, DIGITAL, ETF

    private String description; // "1oz Gold Coin", "916 Gold Chain 25g"

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal weight; // in grams

    private String weightUnit; // g, oz, tola

    private String purity; // 999, 916, 750 (for gold karat)

    @Column(precision = 18, scale = 2)
    private BigDecimal purchasePrice; // total cost

    @Column(precision = 18, scale = 2)
    private BigDecimal currentPrice; // current total value

    private String currency; // SGD, USD, LKR

    private LocalDate purchaseDate;

    private String purchasedFrom; // shop/dealer name

    private String storageLocation; // "Safe deposit box", "Home", "Bank vault"

    @Builder.Default
    private Boolean includeInNetWorth = true;

    @Builder.Default
    private String status = "HELD"; // HELD, SOLD, GIFTED

    @Column(precision = 18, scale = 2)
    private BigDecimal soldPrice;

    private LocalDate soldDate;

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
