package com.myfinance.saas.observability;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Emits structured audit records for security- and billing-significant events
 * (account, subscription, payment, provisioning). Records carry stable event codes and
 * non-sensitive identifiers only — never passwords, tokens, card data, or full PII.
 *
 * Uses a dedicated logger name ("AUDIT") so records can be routed to a separate sink.
 */
@Component
@Slf4j
public class AuditLogger {

    private static final org.slf4j.Logger AUDIT = org.slf4j.LoggerFactory.getLogger("AUDIT");

    public enum Event {
        SIGNUP,
        EMAIL_VERIFIED,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        PASSWORD_RESET,
        PROVISIONED,
        PROVISIONING_FAILED,
        SUBSCRIPTION_STATE_CHANGE,
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED,
        SUBSCRIPTION_CANCELLED,
        TRIAL_EXPIRED,
        ACCESS_UPDATED
    }

    /**
     * Record an audit event.
     *
     * @param event      the event code
     * @param customerId associated customer id (may be null for pre-account events)
     * @param detail     short non-sensitive detail (e.g. "TRIAL->ACTIVE")
     */
    public void record(Event event, Long customerId, String detail) {
        try {
            MDC.put("auditEvent", event.name());
            if (customerId != null) {
                MDC.put("customerId", String.valueOf(customerId));
            }
            AUDIT.info("event={} customerId={} detail={}", event, customerId, detail == null ? "" : detail);
        } finally {
            MDC.remove("auditEvent");
            MDC.remove("customerId");
        }
    }
}
