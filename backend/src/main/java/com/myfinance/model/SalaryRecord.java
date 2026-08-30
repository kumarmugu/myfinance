package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Column(nullable = false, name = "\"year\"")
    private Integer year;

    @Column(nullable = false, name = "\"month\"")
    private Integer month; // 1-12

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private BigDecimal amount; // total take-home

    private BigDecimal basic;
    private BigDecimal allowance;
    private BigDecimal mobile;
    private BigDecimal support;
    private BigDecimal weekend;
    private BigDecimal mealAllowance;
    private BigDecimal deductions; // SINDA, etc.

    // ── Retirement-fund contributions (in this record's currency; all nullable/optional). ──
    // A company may deduct in some months and not others, so any of these can be left blank.
    // Legacy generic fields (kept for backward compatibility; superseded by the scheme columns).
    private BigDecimal employeeContribution;
    private BigDecimal employerContribution;

    // CPF (Singapore)
    private BigDecimal cpfEmployee;
    private BigDecimal cpfEmployer;
    // EPF (Sri Lanka) — employee ~8%, employer ~12%
    private BigDecimal epfEmployee;
    private BigDecimal epfEmployer;
    // ETF (Sri Lanka) — employer only ~3%
    private BigDecimal etfEmployer;

    /** Optional label for the contribution scheme shown in the UI: CPF, EPF_ETF, EPF, ETF, NONE. */
    private String contributionScheme;

    /**
     * Whether the employer actually remitted the contribution to the fund. Defaults to false
     * because deduction from pay does not guarantee remittance — some employers deduct but fail
     * to pay in. The user confirms remittance explicitly (e.g. after checking the EPF/CPF statement).
     */
    @Builder.Default
    private Boolean contributionRemitted = false;

    @Builder.Default
    private Boolean isBonus = false;

    private BigDecimal bonusMonths; // e.g. 4.25

    private String country; // Singapore, Sri Lanka

    /**
     * Original currency of this salary record (e.g. SGD, LKR). Nullable for
     * backward compatibility — existing rows are treated as the user's base
     * currency. The original amount is never overwritten by conversion.
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

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    /** Total employee-side contribution deducted from pay (CPF + EPF employee, plus any legacy generic). */
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal getEmployeeContributionTotal() {
        return nz(cpfEmployee).add(nz(epfEmployee)).add(nz(employeeContribution));
    }

    /** Total employer-side contribution (not deducted from pay): CPF + EPF + ETF employer, plus legacy generic. */
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal getEmployerContributionTotal() {
        return nz(cpfEmployer).add(nz(epfEmployer)).add(nz(etfEmployer)).add(nz(employerContribution));
    }

    /**
     * Net take-home = gross components (basic + allowances) − deductions − employee contributions.
     * Falls back to {@code amount} for gross when the component breakdown isn't provided.
     */
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal getNetTakeHome() {
        BigDecimal components = nz(basic).add(nz(allowance)).add(nz(mobile))
                .add(nz(support)).add(nz(weekend)).add(nz(mealAllowance));
        if (components.compareTo(BigDecimal.ZERO) <= 0) {
            // No component breakdown (e.g. legacy records): `amount` is already the take-home
            // the user entered — return it as-is, never re-subtract deductions/contributions.
            return nz(amount);
        }
        // Full breakdown present: net = gross components − deductions − employee contributions.
        return components.subtract(nz(deductions)).subtract(getEmployeeContributionTotal());
    }
}
