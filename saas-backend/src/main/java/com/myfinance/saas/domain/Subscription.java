package com.myfinance.saas.domain;

import com.myfinance.saas.domain.enums.SubscriptionState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A customer's subscription. State is the single source of truth for access decisions;
 * timestamps capture trial/period boundaries. Provider references (Stripe) are stored,
 * never secrets.
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_customer", columnList = "customer_id"),
        @Index(name = "idx_subscription_state", columnList = "state")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionState state;

    /** When the trial ends (for TRIAL state). */
    private LocalDateTime trialEndsAt;

    /** End of the currently paid period (for ACTIVE/PAST_DUE/CANCELLED). */
    private LocalDateTime currentPeriodEnd;

    /** Set when the customer requests cancellation (may still run until period end). */
    private LocalDateTime cancelledAt;

    /** Set when the subscription finally expires. */
    private LocalDateTime expiredAt;

    /** Stripe subscription id reference (not a secret). */
    private String stripeSubscriptionId;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
