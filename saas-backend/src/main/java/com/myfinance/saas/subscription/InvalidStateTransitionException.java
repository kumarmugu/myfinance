package com.myfinance.saas.subscription;

import com.myfinance.saas.domain.enums.SubscriptionState;

/**
 * Thrown when a subscription state transition is not permitted by the state machine.
 */
public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(SubscriptionState from, SubscriptionState to) {
        super("Invalid subscription state transition: " + from + " -> " + to);
    }
}
