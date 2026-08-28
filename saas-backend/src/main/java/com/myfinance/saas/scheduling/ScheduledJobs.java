package com.myfinance.saas.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Thin scheduling layer that invokes the reconciliation and trial-sweep services on fixed
 * intervals. The business logic lives in the services (unit-testable without the scheduler).
 * Each job is wrapped so a failure in one run does not stop future runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobs {

    private final TrialSweepService trialSweepService;
    private final ProvisioningReconciliationService reconciliationService;

    /** Expire ended trials hourly. */
    @Scheduled(fixedDelayString = "${app.scheduling.trial-sweep-ms:3600000}")
    public void trialSweep() {
        try {
            LocalDateTime now = LocalDateTime.now();
            trialSweepService.expireEndedTrials(now);
            trialSweepService.notifyEndingSoon(now);
        } catch (Exception e) {
            log.error("Trial sweep job failed: {}", e.getMessage());
        }
    }

    /** Retry pending provisioning every 5 minutes. */
    @Scheduled(fixedDelayString = "${app.scheduling.reconciliation-ms:300000}")
    public void reconcileProvisioning() {
        try {
            reconciliationService.reconcile();
        } catch (Exception e) {
            log.error("Provisioning reconciliation job failed: {}", e.getMessage());
        }
    }
}
