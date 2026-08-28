package com.myfinance.saas.subscription;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TrialCalculatorTest {

    private final TrialCalculator calc = new TrialCalculator();

    @Test
    void computesTrialEnd() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        assertEquals(LocalDateTime.of(2026, 1, 8, 10, 0), calc.trialEnd(start, 7));
    }

    @Test
    void isExpiredWhenNowAtOrAfterEnd() {
        LocalDateTime end = LocalDateTime.of(2026, 1, 8, 10, 0);
        assertTrue(calc.isExpired(end, end));                       // exactly at end
        assertTrue(calc.isExpired(end, end.plusSeconds(1)));        // after
        assertFalse(calc.isExpired(end, end.minusMinutes(1)));      // before
    }

    @Test
    void daysRemainingRoundsUpAndNeverNegative() {
        LocalDateTime end = LocalDateTime.of(2026, 1, 8, 10, 0);
        // 7 full days remaining
        assertEquals(7, calc.daysRemaining(end, LocalDateTime.of(2026, 1, 1, 10, 0)));
        // 1 day + 1 hour remaining -> rounds up to 2
        assertEquals(2, calc.daysRemaining(end, LocalDateTime.of(2026, 1, 7, 9, 0)));
        // just under a day remaining -> 1
        assertEquals(1, calc.daysRemaining(end, LocalDateTime.of(2026, 1, 7, 11, 0)));
        // expired -> 0
        assertEquals(0, calc.daysRemaining(end, end));
        assertEquals(0, calc.daysRemaining(end, end.plusDays(3)));
    }

    @Test
    void nullTrialEndTreatedAsNoTimeRemaining() {
        assertEquals(0, calc.daysRemaining(null, LocalDateTime.now()));
        assertFalse(calc.isExpired(null, LocalDateTime.now()));
    }
}
