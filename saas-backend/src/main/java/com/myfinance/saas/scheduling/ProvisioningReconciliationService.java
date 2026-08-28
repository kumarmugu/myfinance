package com.myfinance.saas.scheduling;

import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.email.EmailService;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.integration.FinanceAppException;
import com.myfinance.saas.observability.AuditLogger;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Retries provisioning for customers that were created but not yet provisioned into the
 * finance app (e.g. the finance app was down during signup). Idempotent on the finance-app
 * side, so retries are safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProvisioningReconciliationService {

    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FinanceAppClient financeAppClient;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final AuditLogger auditLogger;

    /** Attempt to provision all not-yet-provisioned customers. Returns the number provisioned. */
    @Transactional
    public int reconcile() {
        if (!financeAppClient.isConfigured()) {
            return 0;
        }
        List<Customer> pending = customerRepository.findByProvisionedFalse();
        int provisioned = 0;
        for (Customer customer : pending) {
            String features = featuresFor(customer);
            try {
                financeAppClient.provisionUser(customer.getEmail(), customer.getFullName(), features);
                customer.setProvisioned(true);
                customerRepository.save(customer);
                auditLogger.record(AuditLogger.Event.PROVISIONED, customer.getId(), "reconciled");
                safeEmail(() -> emailService.sendWelcomeEmail(
                        customer.getEmail(), appProperties.getFinanceAppLoginUrl()));
                provisioned++;
            } catch (FinanceAppException e) {
                auditLogger.record(AuditLogger.Event.PROVISIONING_FAILED, customer.getId(), "retry-failed");
                log.warn("Reconciliation still failing for customerId={}: {}", customer.getId(), e.getMessage());
            }
        }
        if (provisioned > 0) {
            log.info("Reconciliation provisioned {} customer(s)", provisioned);
        }
        return provisioned;
    }

    private String featuresFor(Customer customer) {
        Subscription sub = subscriptionRepository.findByCustomerId(customer.getId()).orElse(null);
        return (sub != null && sub.getPlan() != null) ? sub.getPlan().getEnabledFeatures() : "";
    }

    private void safeEmail(Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            log.error("Welcome email failed (non-fatal): {}", e.getMessage());
        }
    }
}
