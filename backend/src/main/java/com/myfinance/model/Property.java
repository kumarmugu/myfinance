package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "properties")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String propertyName; // "HDB Woodlands", "Condo Tampines"

    private String propertyType; // HDB, CONDO, LANDED, COMMERCIAL, LAND

    private String address;

    private String country; // Singapore, Sri Lanka, etc.

    @Column(precision = 18, scale = 2)
    private BigDecimal purchasePrice;

    @Column(precision = 18, scale = 2)
    private BigDecimal currentValue;

    @Column(precision = 18, scale = 2)
    private BigDecimal outstandingLoan;

    private String currency; // SGD, LKR, USD

    private LocalDate purchaseDate;

    private String tenure; // 99 years, Freehold

    @Column(precision = 8, scale = 2)
    private BigDecimal areaSize; // sqft or sqm

    private String areaUnit; // sqft, sqm

    private String ownership; // SOLE, JOINT

    @Builder.Default
    private Boolean includeInNetWorth = true;

    @Builder.Default
    private String status = "OWNED"; // OWNED, SOLD, RENTED_OUT

    @Column(precision = 18, scale = 2)
    private BigDecimal monthlyRental; // if rented out

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
