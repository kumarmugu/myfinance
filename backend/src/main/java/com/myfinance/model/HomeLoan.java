package com.myfinance.model;

import com.myfinance.model.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "home_loans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HomeLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String propertyName; // e.g. "Condo at Woodlands"

    private String propertyAddress;

    @Column(nullable = false)
    private BigDecimal propertyValue; // market value

    @Column(nullable = false)
    private BigDecimal loanAmount; // original loan amount

    @Column(nullable = false)
    private BigDecimal interestRate; // annual %

    private String loanType; // FIXED, FLOATING, HDB

    @Column(nullable = false)
    private Integer tenureMonths; // total tenure in months

    private BigDecimal monthlyEmi; // monthly installment

    private BigDecimal outstandingBalance; // current outstanding

    private BigDecimal totalPaid; // total paid so far

    private BigDecimal totalInterestPaid;

    private LocalDate startDate;

    private LocalDate expectedEndDate;

    private String bank; // lending bank

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Currency currency = Currency.SGD;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean includeInNetWorth = true; // property value - outstanding = equity

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
