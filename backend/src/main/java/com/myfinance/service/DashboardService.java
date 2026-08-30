package com.myfinance.service;

import com.myfinance.dto.DashboardSummary;
import com.myfinance.model.Holding;
import com.myfinance.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final HoldingService holdingService;
    private final NetWorthService netWorthService;
    private final AccountRepository accountRepository;
    private final CurrencyConversionService fx;

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

        // Investment performance figures (holdings only), converted to base currency (SGD).
        BigDecimal totalInvested = holdings.stream()
                .map(h -> fx.toBase(h.getInvestedAmount(), currencyCode(h), userId))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal investedCurrentValue = holdings.stream()
                .map(h -> {
                    BigDecimal price = h.getAsset().getCurrentPrice();
                    if (price == null) price = h.getAverageBuyPrice();
                    return fx.toBase(h.getQuantity().multiply(price), currencyCode(h), userId);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGainLoss = investedCurrentValue.subtract(totalInvested);
        BigDecimal gainLossPercentage = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            gainLossPercentage = totalGainLoss.multiply(BigDecimal.valueOf(100))
                    .divide(totalInvested, 2, RoundingMode.HALF_UP);
        }

        // Net worth = holdings + standalone modules, FX-converted, respecting config + per-record flags.
        Map<String, BigDecimal> allocation = netWorthService.getCurrentAllocationForUser(userId);
        BigDecimal totalNetWorth = allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Display-currency factors derived from the user's own rates + configured display list.
        // No hardcoded FX. Base currency factor is always 1; others omitted when no rate exists.
        String baseCurrency = fx.getBaseCurrency(userId);
        Map<String, BigDecimal> displayRates = new LinkedHashMap<>();
        for (String code : fx.getDisplayCurrencies(userId)) {
            BigDecimal factor = fx.factorFromBase(code, userId);
            if (factor != null) displayRates.put(code, factor);
        }

        return DashboardSummary.builder()
                .totalNetWorth(totalNetWorth)
                .totalInvested(totalInvested)
                .totalGainLoss(totalGainLoss)
                .gainLossPercentage(gainLossPercentage)
                .allocationByType(allocation)
                .totalHoldings(holdings.size())
                .totalAccounts((int) accountRepository.count())
                .baseCurrency(baseCurrency)
                .displayRates(displayRates)
                .build();
    }

    private String currencyCode(Holding h) {
        return h.getCurrency() != null ? h.getCurrency().name() : null;
    }
}
