package com.myfinance.saas.email;

/**
 * Pluggable email delivery abstraction. Concrete providers (no-op, console, SMTP) are
 * selected by the {@code app.email.provider} property. Callers pass fully-built URLs and
 * non-sensitive display values only — providers never receive secrets or card data.
 */
public interface EmailService {

    // ── account lifecycle ──

    void sendVerificationEmail(String toEmail, String verifyUrl);

    void sendPasswordResetEmail(String toEmail, String resetUrl);

    void sendWelcomeEmail(String toEmail, String loginUrl);

    // ── trial lifecycle ──

    void sendTrialStarted(String toEmail, int trialDays, String loginUrl);

    void sendTrialEndingSoon(String toEmail, long daysRemaining, String upgradeUrl);

    void sendTrialExpired(String toEmail, String upgradeUrl);

    // ── subscription / payment lifecycle ──

    void sendSubscriptionCreated(String toEmail, String planName);

    void sendPaymentSucceeded(String toEmail, String planName, String amount, String currency);

    void sendPaymentFailed(String toEmail, String updatePaymentUrl);

    void sendSubscriptionCancelled(String toEmail, String accessUntil);
}
