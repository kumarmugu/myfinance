package com.myfinance.model;

import com.myfinance.model.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_policies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InsurancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String policyName;

    private String provider; // AIA, Prudential, etc.
    private String policyNumber;
    private String policyType; // TERM_LIFE, WHOLE_LIFE, ENDOWMENT, ILP, HEALTH

    @Column(nullable = false)
    private BigDecimal annualPremium;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.SGD;

    private BigDecimal coverageAmount;
    private BigDecimal cashValue; // current surrender value if any

    private LocalDate startDate;
    private LocalDate maturityDate;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean includeInNetWorth = false;

    private String beneficiary;
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
