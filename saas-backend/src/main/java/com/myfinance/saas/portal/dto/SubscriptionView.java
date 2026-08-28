package com.myfinance.saas.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Customer-facing subscription status for the portal. Reflects the single source-of-truth
 * state plus trial/period info. No provider secrets.
 */
@Data
@Builder
@AllArgsConstructor
public class SubscriptionView {
    private String state;
    private String planCode;
    private String planName;
    private boolean inTrial;
    private LocalDateTime trialEndsAt;
    private long trialDaysRemaining;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime cancelledAt;
    private boolean grantsAccess;
}
