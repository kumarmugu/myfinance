package com.myfinance.saas.observability;

import com.myfinance.saas.integration.FinanceAppClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether the finance-app integration is configured. Contributes to the actuator
 * /health endpoint. Reports non-sensitive status only (never the token value).
 *
 * Note: this is a configuration/readiness signal, not a live ping, to avoid coupling the
 * SaaS backend's health to the finance app's availability (which is handled resiliently
 * via reconciliation).
 */
@Component("financeApp")
@RequiredArgsConstructor
public class FinanceAppHealthIndicator implements HealthIndicator {

    private final FinanceAppClient financeAppClient;

    @Override
    public Health health() {
        if (financeAppClient.isConfigured()) {
            return Health.up().withDetail("provisioning", "configured").build();
        }
        // Not configured is not "down" for the SaaS service, but is surfaced for visibility.
        return Health.up().withDetail("provisioning", "not-configured").build();
    }
}
