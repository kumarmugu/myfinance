package com.myfinance.saas.domain;

import com.myfinance.saas.domain.enums.BillingPeriod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A configurable subscription plan. Pricing and feature mapping live in data (not code),
 * so plans can be added/changed without redeploying. The {@code enabledFeatures} field maps
 * directly to the existing finance app's feature-flag string (empty string = ALL features).
 */
@Entity
@Table(name = "plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable machine code, e.g. "free_trial", "starter", "pro", "premium". */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Placeholder price; set real values in configuration/seed data. */
    @Builder.Default
    private BigDecimal priceAmount = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "SGD";

    @Enumerated(EnumType.STRING)
    private BillingPeriod billingPeriod;

    /** Trial length in days for this plan (0 = no trial). */
    @Builder.Default
    private int trialDays = 0;

    /**
     * Comma-separated feature keys mapped to the finance app. Empty string means ALL.
     * e.g. "PORTFOLIO,DIVIDENDS,REPORTS"
     */
    @Column(length = 1000)
    @Builder.Default
    private String enabledFeatures = "";

    /** Optional Stripe Price ID for this plan (kept in config/DB, not a secret). */
    private String stripePriceId;

    @Builder.Default
    private boolean recommended = false;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int displayOrder = 0;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
