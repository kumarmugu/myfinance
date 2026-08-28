package com.myfinance.saas.domain.enums;

/**
 * Subscription lifecycle states. A single source of truth (no scattered boolean flags).
 *
 * Allowed transitions:
 *   TRIAL     -> ACTIVE | EXPIRED | CANCELLED
 *   ACTIVE    -> PAST_DUE | CANCELLED
 *   PAST_DUE  -> ACTIVE | EXPIRED | CANCELLED
 *   CANCELLED -> EXPIRED
 *   EXPIRED   -> (terminal; may be reactivated to ACTIVE via a new paid subscription)
 */
public enum SubscriptionState {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    EXPIRED
}
