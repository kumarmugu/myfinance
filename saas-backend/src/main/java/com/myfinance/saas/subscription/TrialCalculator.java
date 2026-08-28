package com.myfinance.saas.subscription;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Pure trial date/duration calculations, isolated for straightforward unit testing.
 */
@Component
public class TrialCalculator {

    /** Compute the trial end instant given a start and a trial length in days. */
    public LocalDateTime trialEnd(LocalDateTime start, int trialDays) {
        return start.plusDays(Math.max(0, trialDays));
    }

    /** Whether the trial has expired relative to {@code now}. */
    public boolean isExpired(LocalDateTime trialEndsAt, LocalDateTime now) {
        return trialEndsAt != null && !now.isBefore(trialEndsAt);
    }

    /**
     * Whole days remaining in the trial (rounded up), never negative.
     * Returns 0 once expired.
     */
    public long daysRemaining(LocalDateTime trialEndsAt, LocalDateTime now) {
        if (trialEndsAt == null || !now.isBefore(trialEndsAt)) {
            return 0;
        }
        long minutes = Duration.between(now, trialEndsAt).toMinutes();
        return (long) Math.ceil(minutes / (60.0 * 24.0));
    }
}
