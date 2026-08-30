package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String displayName;

    @Builder.Default
    private String role = "USER"; // ADMIN or USER

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean slFdEnabled = false;

    /**
     * Comma-separated list of enabled feature modules.
     * Available: PORTFOLIO,CRYPTO,DIVIDENDS,CASH_FLOWS,BANK_SAVINGS,FIXED_DEPOSITS,SL_FD,REAL_ESTATE,INSURANCE,HOME_LOANS,SALARY,TAX,WORK_EXPERIENCE,SRS_CPF,REPORTS
     * If null or empty, ALL features are enabled (backward compatible).
     */
    @Column(length = 500)
    private String enabledFeatures;

    /**
     * The user's base/reporting currency for consolidated calculations (e.g. Net Worth).
     * Null is treated as SGD for backward compatibility. This is a reporting concept only;
     * original per-record currencies and amounts are always preserved.
     */
    @Column(length = 10)
    private String baseCurrency;

    /**
     * Comma-separated list of currencies offered in the UI display-currency toggle,
     * e.g. "SGD,USD". Null/empty is treated as "SGD,USD" for backward compatibility.
     * Changing the display currency never mutates stored original values.
     */
    @Column(length = 200)
    private String displayCurrencies;

    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
