package com.myfinance.saas.domain;

import com.myfinance.saas.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A record of a payment attempt/outcome for a customer, for the billing history view.
 * Stores only non-sensitive provider references and metadata — never card data.
 */
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_customer", columnList = "customer_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private BigDecimal amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    /** e.g. "card" or "paynow". */
    private String method;

    /** Stripe payment intent / invoice id reference (not a secret). */
    private String providerReference;

    /** Optional hosted invoice / receipt URL from the provider. */
    private String receiptUrl;

    /** Non-sensitive failure reason for display (never raw provider error internals). */
    private String failureReason;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
