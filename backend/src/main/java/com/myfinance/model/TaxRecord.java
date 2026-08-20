package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer assessmentYear;

    @Column(nullable = false)
    private BigDecimal employment;

    @Builder.Default
    private BigDecimal donations = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal reliefs = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal srsDeduction = BigDecimal.ZERO;

    private BigDecimal chargeableIncome;

    private BigDecimal tax;

    @Builder.Default
    private BigDecimal taxRebate = BigDecimal.ZERO;

    private BigDecimal taxPayable;

    private String country; // Singapore, Sri Lanka, etc.

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
