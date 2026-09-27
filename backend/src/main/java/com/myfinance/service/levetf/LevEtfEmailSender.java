package com.myfinance.service.levetf;

/**
 * Optional email channel for Leveraged ETF alerts. A bean exists ONLY when {@code app.mail.enabled=true}
 * (see {@link LevEtfSmtpEmailSender}); otherwise the alert service's {@code Optional<LevEtfEmailSender>}
 * is empty and email is silently skipped, so in-app notifications work with no SMTP configured.
 */
public interface LevEtfEmailSender {
    void send(Long userId, String subject, String body);
}
