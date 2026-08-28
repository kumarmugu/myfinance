package com.myfinance.saas.domain;

import com.myfinance.saas.domain.enums.CustomerStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A SaaS customer account. Owns portal authentication and (1:1 in v1) maps to a single
 * finance-app user (tenant). Never stores payment card data — only Stripe references.
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customer_email", columnList = "email", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash of the portal password. Never logged or returned. */
    @Column(nullable = false)
    private String passwordHash;

    private String fullName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.PENDING_VERIFICATION;

    @Builder.Default
    private boolean emailVerified = false;

    /** True once the finance-app user has been provisioned (idempotency guard). */
    @Builder.Default
    private boolean provisioned = false;

    /** Whether the customer accepted terms at signup (consent record). */
    @Builder.Default
    private boolean termsAccepted = false;

    private LocalDateTime termsAcceptedAt;

    /** Stripe customer id reference (not a secret). */
    private String stripeCustomerId;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
