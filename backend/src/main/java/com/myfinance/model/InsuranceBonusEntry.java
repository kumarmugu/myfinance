package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_bonus_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InsuranceBonusEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private InsurancePolicy policy;

    @Column(nullable = false)
    private Integer yearNumber; // 1, 2, 3...

    private String yearDate; // e.g. "10/2015"

    private Integer age; // age at that year

    @Column(nullable = false)
    private BigDecimal premiumAmount;

    @Builder.Default
    private BigDecimal expectedBonus = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal expectedBonusTotal = BigDecimal.ZERO; // cumulative

    @Builder.Default
    private BigDecimal expectedTotal = BigDecimal.ZERO; // premium cumulative + bonus cumulative

    @Builder.Default
    private BigDecimal actualBonus = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal actualBonusTotal = BigDecimal.ZERO; // cumulative actual

    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
