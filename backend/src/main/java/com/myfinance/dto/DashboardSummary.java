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
}
