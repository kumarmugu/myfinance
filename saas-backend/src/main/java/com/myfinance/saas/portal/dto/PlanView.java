package com.myfinance.saas.portal.dto;

import com.myfinance.saas.domain.Plan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Public, non-sensitive view of a plan for the pricing page and portal. Excludes internal
 * fields (e.g. Stripe price id is not needed by the browser for display).
 */
@Data
@Builder
@AllArgsConstructor
public class PlanView {
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private String billingPeriod;
    private int trialDays;
    private List<String> features;
    private boolean recommended;
    private int displayOrder;

    public static PlanView from(Plan plan) {
        List<String> features = (plan.getEnabledFeatures() == null || plan.getEnabledFeatures().isBlank())
                ? List.of("ALL")
                : Arrays.stream(plan.getEnabledFeatures().split(",")).map(String::trim).toList();
        return PlanView.builder()
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .price(plan.getPriceAmount())
                .currency(plan.getCurrency())
                .billingPeriod(plan.getBillingPeriod() != null ? plan.getBillingPeriod().name() : null)
                .trialDays(plan.getTrialDays())
                .features(features)
                .recommended(plan.isRecommended())
                .displayOrder(plan.getDisplayOrder())
                .build();
    }
}
