package com.myfinance.saas.subscription;

import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.SubscriptionState;
import org.junit.jupiter.api.Test;

import static com.myfinance.saas.domain.enums.SubscriptionState.*;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionStateMachineTest {

    private final SubscriptionStateMachine sm = new SubscriptionStateMachine();

    private Subscription sub(SubscriptionState state) {
        return Subscription.builder().id(1L).state(state).build();
    }

    @Test
    void allowsValidTrialTransitions() {
        assertTrue(sm.canTransition(TRIAL, ACTIVE));
        assertTrue(sm.canTransition(TRIAL, EXPIRED));
        assertTrue(sm.canTransition(TRIAL, CANCELLED));
    }

    @Test
    void allowsActiveToPastDueAndBack() {
        assertTrue(sm.canTransition(ACTIVE, PAST_DUE));
        assertTrue(sm.canTransition(PAST_DUE, ACTIVE));
    }

    @Test
    void allowsCancelAndExpiryChain() {
        assertTrue(sm.canTransition(ACTIVE, CANCELLED));
        assertTrue(sm.canTransition(CANCELLED, EXPIRED));
    }

    @Test
    void rejectsInvalidTransitions() {
        assertFalse(sm.canTransition(TRIAL, PAST_DUE));
        assertFalse(sm.canTransition(EXPIRED, TRIAL));
        assertFalse(sm.canTransition(CANCELLED, ACTIVE));
    }

    @Test
    void sameStateIsIdempotentNoOp() {
        assertTrue(sm.canTransition(ACTIVE, ACTIVE));
        Subscription s = sub(ACTIVE);
        sm.transition(s, ACTIVE);
        assertEquals(ACTIVE, s.getState());
    }

    @Test
    void transitionThrowsOnInvalid() {
        Subscription s = sub(TRIAL);
        assertThrows(InvalidStateTransitionException.class, () -> sm.transition(s, PAST_DUE));
        // state unchanged after failed transition
        assertEquals(TRIAL, s.getState());
    }

    @Test
    void cancelSetsCancelledAt() {
        Subscription s = sub(ACTIVE);
        sm.transition(s, CANCELLED);
        assertEquals(CANCELLED, s.getState());
        assertNotNull(s.getCancelledAt());
    }

    @Test
    void expireSetsExpiredAt() {
        Subscription s = sub(CANCELLED);
        sm.transition(s, EXPIRED);
        assertEquals(EXPIRED, s.getState());
        assertNotNull(s.getExpiredAt());
    }

    @Test
    void reactivationFromExpiredClearsMarkers() {
        Subscription s = sub(EXPIRED);
        s.setExpiredAt(java.time.LocalDateTime.now());
        sm.transition(s, ACTIVE);
        assertEquals(ACTIVE, s.getState());
        assertNull(s.getExpiredAt());
        assertNull(s.getCancelledAt());
    }

    @Test
    void grantsAccessMatrix() {
        assertTrue(sm.grantsAccess(TRIAL));
        assertTrue(sm.grantsAccess(ACTIVE));
        assertTrue(sm.grantsAccess(PAST_DUE));
        assertTrue(sm.grantsAccess(CANCELLED)); // access until period end
        assertFalse(sm.grantsAccess(EXPIRED));
    }
}
