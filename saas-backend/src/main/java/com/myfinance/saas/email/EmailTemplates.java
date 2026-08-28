package com.myfinance.saas.email;

import com.myfinance.saas.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Renders branded, configurable email content. Bodies are simple, professional, and consistent
 * with the website branding (brand name from config). Templates deliberately avoid sensitive
 * data — they carry only display values and pre-built action URLs.
 *
 * HTML is minimal and inline-styled for broad email-client compatibility. All dynamic values
 * are HTML-escaped to prevent injection into the rendered email.
 */
@Component
@RequiredArgsConstructor
public class EmailTemplates {

    private final AppProperties appProperties;

    private String brand() {
        return appProperties.getEmail().getFromName();
    }

    public EmailContent verification(String verifyUrl) {
        String subject = "Verify your " + brand() + " email";
        String body = "Welcome to " + brand() + "! Please confirm your email address to activate your account.";
        return wrap(subject, body, "Verify email", verifyUrl,
                "If you didn't create an account, you can ignore this email.");
    }

    public EmailContent passwordReset(String resetUrl) {
        String subject = "Reset your " + brand() + " password";
        String body = "We received a request to reset your password. Click the button below to choose a new one. This link expires soon.";
        return wrap(subject, body, "Reset password", resetUrl,
                "If you didn't request this, you can safely ignore this email.");
    }

    public EmailContent welcome(String loginUrl) {
        String subject = "Welcome to " + brand();
        String body = "Your account is ready. You can now sign in and start managing your finances.";
        return wrap(subject, body, "Go to app", loginUrl, null);
    }

    public EmailContent trialStarted(int trialDays, String loginUrl) {
        String subject = "Your " + brand() + " free trial has started";
        String body = "Your " + trialDays + "-day free trial is active. Explore all the features — no payment required during the trial.";
        return wrap(subject, body, "Start exploring", loginUrl, null);
    }

    public EmailContent trialEndingSoon(long daysRemaining, String upgradeUrl) {
        String subject = "Your " + brand() + " trial ends soon";
        String body = "Your free trial ends in " + daysRemaining + " day(s). Upgrade now to keep uninterrupted access.";
        return wrap(subject, body, "Choose a plan", upgradeUrl, null);
    }

    public EmailContent trialExpired(String upgradeUrl) {
        String subject = "Your " + brand() + " trial has ended";
        String body = "Your free trial has ended. Upgrade to a paid plan to regain access to your account.";
        return wrap(subject, body, "Upgrade now", upgradeUrl, null);
    }

    public EmailContent subscriptionCreated(String planName) {
        // Dynamic values are escaped once by wrap()/text builder; do not pre-escape here.
        String subject = "You're subscribed to " + planName;
        String body = "Thank you for subscribing to the " + planName + " plan. Your subscription is now active.";
        return wrap(subject, body, null, null, null);
    }

    public EmailContent paymentSucceeded(String planName, String amount, String currency) {
        String subject = "Payment received - " + brand();
        String body = "We received your payment of " + currency + " " + amount
                + " for the " + planName + " plan. Thank you!";
        return wrap(subject, body, null, null, null);
    }

    public EmailContent paymentFailed(String updatePaymentUrl) {
        String subject = "Action needed: payment failed - " + brand();
        String body = "We couldn't process your latest payment. Please update your payment method to keep your subscription active.";
        return wrap(subject, body, "Update payment", updatePaymentUrl, null);
    }

    public EmailContent subscriptionCancelled(String accessUntil) {
        String subject = "Your " + brand() + " subscription was cancelled";
        String tail = (accessUntil != null && !accessUntil.isBlank())
                ? " You'll keep access until " + accessUntil + "." : "";
        String body = "Your subscription has been cancelled." + tail + " We're sorry to see you go.";
        return wrap(subject, body, null, null, null);
    }

    // ── rendering helpers ──

    private EmailContent wrap(String subject, String bodyText, String ctaLabel, String ctaUrl, String footerNote) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1e293b\">");
        html.append("<h2 style=\"color:#4f46e5\">").append(esc(brand())).append("</h2>");
        html.append("<p style=\"font-size:15px;line-height:1.5\">").append(esc(bodyText)).append("</p>");
        if (ctaLabel != null && ctaUrl != null) {
            html.append("<p><a href=\"").append(escAttr(ctaUrl))
                .append("\" style=\"display:inline-block;background:#4f46e5;color:#fff;text-decoration:none;")
                .append("padding:10px 20px;border-radius:8px;font-weight:bold\">")
                .append(esc(ctaLabel)).append("</a></p>");
        }
        if (footerNote != null) {
            html.append("<p style=\"font-size:12px;color:#64748b\">").append(esc(footerNote)).append("</p>");
        }
        html.append("<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:16px 0\">");
        html.append("<p style=\"font-size:11px;color:#94a3b8\">").append(esc(brand()))
            .append(" · This is an automated message.</p></div>");

        StringBuilder text = new StringBuilder();
        text.append(bodyText).append("\n");
        if (ctaLabel != null && ctaUrl != null) {
            text.append("\n").append(ctaLabel).append(": ").append(ctaUrl).append("\n");
        }
        if (footerNote != null) {
            text.append("\n").append(footerNote).append("\n");
        }
        return new EmailContent(subject, html.toString(), text.toString());
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** URL attribute escaping (escape quotes/angle brackets; do not encode the whole URL). */
    private String escAttr(String s) {
        if (s == null) return "";
        return s.replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
