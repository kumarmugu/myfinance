package com.myfinance.service;

import com.myfinance.dto.DashboardSummary;
import com.myfinance.model.Holding;
import com.myfinance.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final HoldingService holdingService;
    private final NetWorthService netWorthService;
    private final AccountRepository accountRepository;

    public DashboardSummary getSummary(Long ownerId) {
        return getSummary(ownerId, null);
    }

    public DashboardSummary getSummary(Long ownerId, Long userId) {
        log.info("Generating dashboard summary for ownerId={}, userId={}", ownerId, userId);
        List<Holding> holdings;
        if (ownerId != null) {
            holdings = holdingService.getActiveByOwner(ownerId);
        } else if (userId != null) {
            holdings = holdingService.getActiveByUserId(userId);
        } else {
            holdings = holdingService.getActiveHoldings();
        }

        BigDecimal totalInvested = holdings.stream()
                .map(Holding::getInvestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCurrentValue = holdings.stream()
                .map(h -> {
                    BigDecimal price = h.getAsset().getCurrentPrice();
                    if (price == null) price = h.getAverageBuyPrice();
                    return h.getQuantity().multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGainLoss = totalCurrentValue.subtract(totalInvested);
        BigDecimal gainLossPercentage = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            gainLossPercentage = totalGainLoss.multiply(BigDecimal.valueOf(100))
                    .divide(totalInvested, 2, RoundingMode.HALF_UP);
        }

        Map<String, BigDecimal> allocation = netWorthService.getCurrentAllocation();

        return DashboardSummary.builder()
                .totalNetWorth(totalCurrentValue)
                .totalInvested(totalInvested)
                .totalGainLoss(totalGainLoss)
                .gainLossPercentage(gainLossPercentage)
                .allocationByType(allocation)
                .totalHoldings(holdings.size())
                .totalAccounts((int) accountRepository.count())
                .build();
    }
}
