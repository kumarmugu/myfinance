package com.myfinance.service;

import com.myfinance.model.Holding;
import com.myfinance.model.NetWorthConfig;
import com.myfinance.model.NetWorthSnapshot;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.NetWorthConfigRepository;
import com.myfinance.repository.NetWorthSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NetWorthService {
    private final NetWorthSnapshotRepository snapshotRepository;
    private final NetWorthConfigRepository netWorthConfigRepository;
    private final HoldingService holdingService;

    private Set<String> getIncludedTypes() {
        List<NetWorthConfig> configs = netWorthConfigRepository.findByIncludeInNetWorthTrue();
        if (configs.isEmpty()) {
            // If no config exists yet, include everything
            return Arrays.stream(AssetType.values()).map(Enum::name).collect(Collectors.toSet());
        }
        return configs.stream().map(NetWorthConfig::getAssetType).collect(Collectors.toSet());
    }

    public NetWorthSnapshot takeSnapshot(Long ownerId) {
        List<Holding> holdings = ownerId != null
                ? holdingService.getActiveByOwner(ownerId)
                : holdingService.getActiveHoldings();

        // Filter by net worth config (which asset types are included)
        Set<String> includedTypes = getIncludedTypes();
        holdings = holdings.stream()
                .filter(h -> includedTypes.contains(h.getAsset().getAssetType().name()))
                .collect(Collectors.toList());

        Map<AssetType, BigDecimal> totals = holdings.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getAsset().getAssetType(),
                        Collectors.reducing(BigDecimal.ZERO, this::holdingValue, BigDecimal::add)));

        NetWorthSnapshot snapshot = NetWorthSnapshot.builder()
                .snapshotDate(LocalDate.now())
                .year(LocalDate.now().getYear())
                .totalIndexFund(totals.getOrDefault(AssetType.INDEX_FUND, BigDecimal.ZERO))
                .totalMutualFund(totals.getOrDefault(AssetType.MUTUAL_FUND, BigDecimal.ZERO))
                .totalGrowthEquity(totals.getOrDefault(AssetType.GROWTH_EQUITY, BigDecimal.ZERO))
                .totalDividendEquity(totals.getOrDefault(AssetType.DIVIDEND_EQUITY, BigDecimal.ZERO))
                .totalLeveragedEtf(totals.getOrDefault(AssetType.LEVERAGED_ETF, BigDecimal.ZERO))
                .totalMoneyMarket(totals.getOrDefault(AssetType.MONEY_MARKET, BigDecimal.ZERO))
                .totalFixedDeposit(totals.getOrDefault(AssetType.FIXED_DEPOSIT, BigDecimal.ZERO))
                .totalSavings(totals.getOrDefault(AssetType.SAVINGS, BigDecimal.ZERO))
                .totalCrypto(totals.getOrDefault(AssetType.CRYPTO, BigDecimal.ZERO))
                .build();

        BigDecimal total = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        snapshot.setTotalNetWorth(total);
        return snapshotRepository.save(snapshot);
    }

    public List<NetWorthSnapshot> getHistory() { return snapshotRepository.findAllByOrderBySnapshotDateDesc(); }
    public List<NetWorthSnapshot> getByOwner(Long ownerId) { return snapshotRepository.findByOwnerIdOrderBySnapshotDateDesc(ownerId); }
    public Optional<NetWorthSnapshot> getLatest() { return snapshotRepository.findTopByOrderBySnapshotDateDesc(); }

    public Map<String, BigDecimal> getCurrentAllocation() {
        Set<String> includedTypes = getIncludedTypes();
        List<Holding> holdings = holdingService.getActiveHoldings().stream()
                .filter(h -> includedTypes.contains(h.getAsset().getAssetType().name()))
                .collect(Collectors.toList());
        return holdings.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getAsset().getAssetType().name(),
                        Collectors.reducing(BigDecimal.ZERO, this::holdingValue, BigDecimal::add)));
    }

    private BigDecimal holdingValue(Holding h) {
        BigDecimal price = h.getAsset().getCurrentPrice();
        if (price == null) price = h.getAverageBuyPrice();
        return h.getQuantity().multiply(price);
    }
}
