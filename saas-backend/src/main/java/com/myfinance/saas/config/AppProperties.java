package com.myfinance.saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed binding for the SaaS platform's "app.*" configuration.
 * Secrets are sourced from environment variables (see application.yml) — never hardcoded.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Trial trial = new Trial();
    private FinanceApp financeApp = new FinanceApp();
    private Payment payment = new Payment();
    private Stripe stripe = new Stripe();
    private Email email = new Email();
    private String publicWebUrl = "http://localhost:5174";
    private String financeAppLoginUrl = "http://localhost:5173";

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:5174";
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expiration = 3_600_000L;
        private long verificationExpiration = 86_400_000L;
        private long resetExpiration = 3_600_000L;
    }

    @Data
    public static class Trial {
        private int days = 7;
    }

    @Data
    public static class FinanceApp {
        private String baseUrl = "http://localhost:8080";
        private String provisioningToken = "";
    }

    @Data
    public static class Payment {
        /** stripe (default). Other providers can be added behind the PaymentProvider interface. */
        private String provider = "stripe";
    }

    @Data
    public static class Stripe {
        private String secretKey = "";
        private String publishableKey = "";
        private String webhookSecret = "";
    }

    @Data
    public static class Email {
        /** noop | console | smtp */
        private String provider = "noop";
        private String from = "no-reply@myfinance.local";
        private String fromName = "MyFinance";
    }
}
