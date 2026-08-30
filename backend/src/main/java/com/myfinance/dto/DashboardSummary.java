package com.myfinance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class DashboardSummary {
    private BigDecimal totalNetWorth;
    private BigDecimal totalInvested;
    private BigDecimal totalGainLoss;
    private BigDecimal gainLossPercentage;
    private Map<String, BigDecimal> allocationByType;
    private int totalHoldings;
    private int totalAccounts;

    /** Currency all monetary values in this summary are expressed in (net-worth base). */
    private String baseCurrency;

    /**
     * Conversion factors FROM the base currency to other display currencies,
     * derived from the user's own FX rates (no hardcoded values). e.g.
     * {"USD": 0.74} means baseAmount * 0.74 = USD. Only currencies with a
     * usable rate are present; the frontend shows the base currency otherwise.
     */
    private Map<String, BigDecimal> displayRates;
}
