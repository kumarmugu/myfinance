package com.myfinance.saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MyFinance SaaS platform backend.
 *
 * A separate service (from the existing finance app) responsible for public signup,
 * subscription lifecycle, Stripe payments, notifications, and secure provisioning into
 * the existing MyFinance application via its internal provisioning API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SaasApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaasApplication.class, args);
    }
}
