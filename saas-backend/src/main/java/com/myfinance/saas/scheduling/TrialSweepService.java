package com.myfinance.saas.scheduling;

import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.email.EmailService;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.integration.FinanceAppException;
import com.myfinance.saas.observability.AuditLogger;
import com.myfinance.saas.repository.SubscriptionRepository;
import com.myfinance.saas.subscription.SubscriptionStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled trial lifecycle sweeps:
 * - expireEndedTrials: TRIAL past its end date with no paid subscription -> EXPIRED, suspend
 *   finance-app access, and send a "trial expired" email.
 * - notifyEndingSoon: TRIAL ending within the reminder window -> send a "trial ending soon" email.
 *
 * Methods are package-visible/public and side-effect-contained so they can be unit tested
 * without the scheduler. Individual failures are isolated so one bad row doesn't halt the sweep.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrialSweepService {

    private static final int ENDING_SOON_DAYS = 2;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStateMachine stateMachine;
    private final EmailService emailService;
    private final FinanceAppClient financeAppClient;
    private final AppProperties appProperties;
    private final AuditLogger auditLogger;

    /** Expire trials whose end date has passed. Returns the number expired. */
    @Transactional
    public int expireEndedTrials(LocalDateTime now) {
        List<Subscription> ended = subscriptionRepository
                .findByStateAndTrialEndsAtBefore(SubscriptionState.TRIAL, now);
        int count = 0;
        for (Subscription sub : ended) {
            try {
                stateMachine.transition(sub, SubscriptionState.EXPIRED);
                subscriptionRepository.save(sub);
                suspendAccess(sub);
                Customer c = sub.getCustomer();
                if (c != null) {
                    safeEmail(() -> emailService.sendTrialExpired(c.getEmail(),
                            appProperties.getPublicWebUrl() + "/pricing"));
                    auditLogger.record(AuditLogger.Event.TRIAL_EXPIRED, c.getId(), "TRIAL->EXPIRED");
                }
                count++;
            } catch (Exception e) {
                log.error("Failed to expire trial subscriptionId={}: {}", sub.getId(), e.getMessage());
            }
        }
        if (count > 0) {
            log.info("Trial sweep expired {} subscription(s)", count);
        }
        return count;
    }

    /** Notify trials ending within the reminder window. Returns the number notified. */
    @Transactional(readOnly = true)
    public int notifyEndingSoon(LocalDateTime now) {
        LocalDateTime windowEnd = now.plusDays(ENDING_SOON_DAYS);
        List<Subscription> ending = subscriptionRepository
                .findByStateAndTrialEndsAtBetween(SubscriptionState.TRIAL, now, windowEnd);
        int count = 0;
        for (Subscription sub : ending) {
            Customer c = sub.getCustomer();
            if (c == null) {
                continue;
            }
            long daysLeft = java.time.Duration.between(now, sub.getTrialEndsAt()).toDays() + 1;
            safeEmail(() -> emailService.sendTrialEndingSoon(c.getEmail(), daysLeft,
                    appProperties.getPublicWebUrl() + "/pricing"));
            count++;
        }
        if (count > 0) {
            log.info("Trial sweep notified {} customer(s) of ending trials", count);
        }
        return count;
    }

    private void suspendAccess(Subscription sub) {
        Customer c = sub.getCustomer();
        if (c == null || !financeAppClient.isConfigured()) {
            return;
        }
        try {
            financeAppClient.updateAccess(c.getEmail(), false, null);
            auditLogger.record(AuditLogger.Event.ACCESS_UPDATED, c.getId(), "suspended");
        } catch (FinanceAppException e) {
            log.error("Failed to suspend finance access for customerId={}: {}", c.getId(), e.getMessage());
        }
    }

    private void safeEmail(Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            log.error("Trial email failed (non-fatal): {}", e.getMessage());
        }
    }
}
