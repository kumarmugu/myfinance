package com.myfinance.saas.email;

import com.myfinance.saas.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplatesTest {

    private EmailTemplates templates;

    @BeforeEach
    void setup() {
        AppProperties props = new AppProperties();
        props.getEmail().setFromName("MyFinance");
        templates = new EmailTemplates(props);
    }

    @Test
    void verificationIncludesBrandAndCtaUrl() {
        EmailContent c = templates.verification("https://web.test/verify-email?token=abc");
        assertTrue(c.subject().contains("MyFinance"));
        assertTrue(c.html().contains("https://web.test/verify-email?token=abc"));
        assertTrue(c.text().contains("https://web.test/verify-email?token=abc"));
    }

    @Test
    void passwordResetHasResetCta() {
        EmailContent c = templates.passwordReset("https://web.test/reset-password?token=xyz");
        assertTrue(c.subject().toLowerCase().contains("reset"));
        assertTrue(c.html().contains("Reset password"));
    }

    @Test
    void trialEndingSoonShowsDaysRemaining() {
        EmailContent c = templates.trialEndingSoon(2, "https://web.test/pricing");
        assertTrue(c.text().contains("2 day"));
    }

    @Test
    void paymentSucceededShowsAmountAndCurrency() {
        EmailContent c = templates.paymentSucceeded("Pro", "9.90", "SGD");
        assertTrue(c.text().contains("SGD"));
        assertTrue(c.text().contains("9.90"));
        assertTrue(c.text().contains("Pro"));
    }

    @Test
    void subscriptionCancelledShowsAccessUntil() {
        EmailContent c = templates.subscriptionCancelled("2026-09-30");
        assertTrue(c.text().contains("2026-09-30"));
    }

    @Test
    void htmlEscapesDynamicValuesToPreventInjection() {
        // A plan name containing HTML must be escaped in the rendered HTML body.
        EmailContent c = templates.subscriptionCreated("<script>alert(1)</script>");
        assertFalse(c.html().contains("<script>alert(1)</script>"),
                "Dynamic values must be HTML-escaped");
        assertTrue(c.html().contains("&lt;script&gt;"));
    }

    @Test
    void allTemplatesProduceNonEmptySubjectAndBodies() {
        EmailContent[] all = {
                templates.verification("u"),
                templates.passwordReset("u"),
                templates.welcome("u"),
                templates.trialStarted(7, "u"),
                templates.trialEndingSoon(1, "u"),
                templates.trialExpired("u"),
                templates.subscriptionCreated("Pro"),
                templates.paymentSucceeded("Pro", "1.00", "SGD"),
                templates.paymentFailed("u"),
                templates.subscriptionCancelled("2026-01-01"),
        };
        for (EmailContent c : all) {
            assertNotNull(c.subject());
            assertFalse(c.subject().isBlank());
            assertFalse(c.html().isBlank());
            assertFalse(c.text().isBlank());
        }
    }
}
