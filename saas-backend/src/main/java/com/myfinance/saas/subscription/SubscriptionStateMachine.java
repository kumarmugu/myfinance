package com.myfinance.saas.subscription;

import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.SubscriptionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.myfinance.saas.domain.enums.SubscriptionState.*;

/**
 * Central authority for subscription state transitions.
 *
 * Encapsulates which transitions are legal and applies the associated timestamp side effects,
 * so state is always consistent regardless of the trigger (payment webhook, trial sweep,
 * cancellation request). Illegal transitions throw {@link InvalidStateTransitionException}.
 */
@Component
@Slf4j
public class SubscriptionStateMachine {

    private static final Map<SubscriptionState, Set<SubscriptionState>> ALLOWED = new EnumMap<>(SubscriptionState.class);

    static {
        ALLOWED.put(TRIAL, EnumSet.of(ACTIVE, EXPIRED, CANCELLED));
        ALLOWED.put(ACTIVE, EnumSet.of(PAST_DUE, CANCELLED, EXPIRED));
        ALLOWED.put(PAST_DUE, EnumSet.of(ACTIVE, EXPIRED, CANCELLED));
        ALLOWED.put(CANCELLED, EnumSet.of(EXPIRED));
        // EXPIRED is terminal for a given subscription; reactivation creates a fresh ACTIVE
        // period via ACTIVE below to support "resubscribe".
        ALLOWED.put(EXPIRED, EnumSet.of(ACTIVE));
    }

    public boolean canTransition(SubscriptionState from, SubscriptionState to) {
        if (from == to) {
            return true; // idempotent no-op
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(SubscriptionState.class)).contains(to);
    }

    /**
     * Apply a transition to the given subscription, mutating its state and relevant timestamps.
     * No-op if already in the target state (idempotent).
     *
     * @throws InvalidStateTransitionException if the transition is not allowed.
     */
    public void transition(Subscription subscription, SubscriptionState to) {
        SubscriptionState from = subscription.getState();
        if (from == to) {
            return;
        }
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }

        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case CANCELLED -> {
                if (subscription.getCancelledAt() == null) {
                    subscription.setCancelledAt(now);
                }
            }
            case EXPIRED -> subscription.setExpiredAt(now);
            case ACTIVE -> {
                // Re-activation clears prior cancel/expire markers.
                subscription.setCancelledAt(null);
                subscription.setExpiredAt(null);
            }
            default -> { /* PAST_DUE / TRIAL: no timestamp side effects here */ }
        }

        subscription.setState(to);
        log.info("Subscription id={} transitioned {} -> {}", subscription.getId(), from, to);
    }

    /**
     * Whether a subscription in the given state currently grants access to the finance app.
     * PAST_DUE grants access during its grace window (grace handling lives in the service layer).
     */
    public boolean grantsAccess(SubscriptionState state) {
        return state == TRIAL || state == ACTIVE || state == PAST_DUE || state == CANCELLED;
    }
}
