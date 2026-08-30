package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "retirement_fund_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetirementFundEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Column(nullable = false)
    private String fundType; // CPF, EPF, SPF, SRS

    @Column(nullable = false)
    private String entryType; // CONTRIBUTION, WITHDRAWAL, INTEREST, EMPLOYER_CONTRIBUTION

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(name = "\"year\"")
    private Integer year;
    @Column(name = "\"month\"")
    private Integer month;

    private String account; // OA, SA, MA for CPF; or employee/employer for EPF

    private BigDecimal balance; // running balance after this entry

    private String employer; // company name for employer contributions

    /**
     * Original currency (e.g. SGD for CPF/SRS, LKR for EPF). Nullable for backward
     * compatibility — existing rows are treated as the user's base currency.
     */
    @Column(length = 10)
    private String currency;

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
