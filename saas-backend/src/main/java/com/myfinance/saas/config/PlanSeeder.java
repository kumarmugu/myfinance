package com.myfinance.saas.config;

import com.myfinance.saas.domain.Plan;
import com.myfinance.saas.domain.enums.BillingPeriod;
import com.myfinance.saas.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds default subscription plans if none exist. Prices are PLACEHOLDERS — set real values
 * in configuration/admin before going live. Feature sets map to the finance app's feature
 * flags (empty string = ALL features).
 *
 * TODO(pricing): replace placeholder priceAmount and stripePriceId values with real ones.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanSeeder implements CommandLineRunner {

    private final PlanRepository planRepository;

    @Override
    public void run(String... args) {
        if (planRepository.count() > 0) {
            return;
        }
        log.info("Seeding default subscription plans (placeholder pricing)");

        planRepository.save(Plan.builder()
                .code("free_trial").name("Free Trial")
                .description("Full access for 7 days. No card required.")
                .priceAmount(BigDecimal.ZERO).currency("SGD")
                .billingPeriod(BillingPeriod.MONTHLY).trialDays(7)
                .enabledFeatures("PORTFOLIO,DIVIDENDS,CASH_FLOWS,REPORTS")
                .recommended(false).active(true).displayOrder(0)
                .build());

        planRepository.save(Plan.builder()
                .code("starter").name("Starter")
                .description("Core investment tracking for individuals.")
                .priceAmount(new BigDecimal("0.00")).currency("SGD")   // TODO: real price
                .billingPeriod(BillingPeriod.MONTHLY).trialDays(0)
                .enabledFeatures("PORTFOLIO,DIVIDENDS,CASH_FLOWS,REPORTS")
                .recommended(false).active(true).displayOrder(1)
                .build());

        planRepository.save(Plan.builder()
                .code("pro").name("Pro")
                .description("For serious investors managing multiple asset classes.")
                .priceAmount(new BigDecimal("0.00")).currency("SGD")   // TODO: real price
                .billingPeriod(BillingPeriod.MONTHLY).trialDays(0)
                .enabledFeatures("PORTFOLIO,DIVIDENDS,CASH_FLOWS,REPORTS,CRYPTO,BANK_SAVINGS,FIXED_DEPOSITS,BUDGET,SRS_CPF,TAX")
                .recommended(true).active(true).displayOrder(2)
                .build());

        planRepository.save(Plan.builder()
                .code("premium").name("Premium")
                .description("Everything MyFinance offers, all features unlocked.")
                .priceAmount(new BigDecimal("0.00")).currency("SGD")   // TODO: real price
                .billingPeriod(BillingPeriod.MONTHLY).trialDays(0)
                .enabledFeatures("")   // empty = ALL features (finance-app convention)
                .recommended(false).active(true).displayOrder(3)
                .build());

        log.info("Seeded {} plans", planRepository.count());
    }
}
