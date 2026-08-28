package com.myfinance.saas.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default email provider used when {@code app.email.provider} is "noop" (the default).
 * Logs (at INFO) that an email would be sent, with the address masked. Token-bearing URLs
 * are only logged at DEBUG for local development convenience — never at INFO.
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "noop", matchIfMissing = true)
@Slf4j
public class NoOpEmailService implements EmailService {

    @Override
    public void sendVerificationEmail(String toEmail, String verifyUrl) {
        log.info("[email:noop] verification email queued for {}", EmailMasking.mask(toEmail));
        log.debug("[email:noop] verification link: {}", verifyUrl);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        log.info("[email:noop] password reset email queued for {}", EmailMasking.mask(toEmail));
        log.debug("[email:noop] reset link: {}", resetUrl);
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String loginUrl) {
        log.info("[email:noop] welcome email queued for {}", EmailMasking.mask(toEmail));
    }

    @Override
    public void sendTrialStarted(String toEmail, int trialDays, String loginUrl) {
        log.info("[email:noop] trial-started email queued for {} ({} days)", EmailMasking.mask(toEmail), trialDays);
    }

    @Override
    public void sendTrialEndingSoon(String toEmail, long daysRemaining, String upgradeUrl) {
        log.info("[email:noop] trial-ending email queued for {} ({} days left)", EmailMasking.mask(toEmail), daysRemaining);
    }

    @Override
    public void sendTrialExpired(String toEmail, String upgradeUrl) {
        log.info("[email:noop] trial-expired email queued for {}", EmailMasking.mask(toEmail));
    }

    @Override
    public void sendSubscriptionCreated(String toEmail, String planName) {
        log.info("[email:noop] subscription-created email queued for {} (plan={})", EmailMasking.mask(toEmail), planName);
    }

    @Override
    public void sendPaymentSucceeded(String toEmail, String planName, String amount, String currency) {
        log.info("[email:noop] payment-succeeded email queued for {}", EmailMasking.mask(toEmail));
    }

    @Override
    public void sendPaymentFailed(String toEmail, String updatePaymentUrl) {
        log.info("[email:noop] payment-failed email queued for {}", EmailMasking.mask(toEmail));
    }

    @Override
    public void sendSubscriptionCancelled(String toEmail, String accessUntil) {
        log.info("[email:noop] subscription-cancelled email queued for {}", EmailMasking.mask(toEmail));
    }
}
