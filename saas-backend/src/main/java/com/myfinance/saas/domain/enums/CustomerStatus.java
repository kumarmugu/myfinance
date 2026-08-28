package com.myfinance.saas.domain.enums;

/**
 * Lifecycle status of a SaaS customer account (distinct from subscription state).
 */
public enum CustomerStatus {
    /** Account created, email not yet verified. */
    PENDING_VERIFICATION,
    /** Email verified; account usable. */
    ACTIVE,
    /** Administratively or self-suspended. */
    SUSPENDED
}
