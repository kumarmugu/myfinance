package com.myfinance.saas.email;

import lombok.RequiredArgsConstructor;

/**
 * Base for providers that render templates and then deliver them via a concrete transport.
 * Subclasses implement {@link #deliver(String, EmailContent)}.
 */
@RequiredArgsConstructor
public abstract class AbstractTemplatedEmailService implements EmailService {

    protected final EmailTemplates templates;

    protected abstract void deliver(String toEmail, EmailContent content);

    @Override
    public void sendVerificationEmail(String toEmail, String verifyUrl) {
        deliver(toEmail, templates.verification(verifyUrl));
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        deliver(toEmail, templates.passwordReset(resetUrl));
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String loginUrl) {
        deliver(toEmail, templates.welcome(loginUrl));
    }

    @Override
    public void sendTrialStarted(String toEmail, int trialDays, String loginUrl) {
        deliver(toEmail, templates.trialStarted(trialDays, loginUrl));
    }

    @Override
    public void sendTrialEndingSoon(String toEmail, long daysRemaining, String upgradeUrl) {
        deliver(toEmail, templates.trialEndingSoon(daysRemaining, upgradeUrl));
    }

    @Override
    public void sendTrialExpired(String toEmail, String upgradeUrl) {
        deliver(toEmail, templates.trialExpired(upgradeUrl));
    }

    @Override
    public void sendSubscriptionCreated(String toEmail, String planName) {
        deliver(toEmail, templates.subscriptionCreated(planName));
    }

    @Override
    public void sendPaymentSucceeded(String toEmail, String planName, String amount, String currency) {
        deliver(toEmail, templates.paymentSucceeded(planName, amount, currency));
    }

    @Override
    public void sendPaymentFailed(String toEmail, String updatePaymentUrl) {
        deliver(toEmail, templates.paymentFailed(updatePaymentUrl));
    }

    @Override
    public void sendSubscriptionCancelled(String toEmail, String accessUntil) {
        deliver(toEmail, templates.subscriptionCancelled(accessUntil));
    }
}
