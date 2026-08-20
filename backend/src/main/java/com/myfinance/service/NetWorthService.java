package com.myfinance.service;

import com.myfinance.model.Holding;
import com.myfinance.model.NetWorthSnapshot;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.NetWorthSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NetWorthService {
    private final NetWorthSnapshotRepository snapshotRepository;
    private final HoldingService holdingService;

    public NetWorthSnapshot takeSnapshot(Long ownerId) {
        List<Holding> holdings = ownerId != null
                ? holdingService.getActiveByOwner(ownerId)
                : holdingService.getActiveHoldings();

        // Only include holdings where asset is flagged for net worth
        holdings = holdings.stream()
                .filter(h -> h.getAsset().getIncludeInNetWorth() == null || h.getAsset().getIncludeInNetWorth())
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
        List<Holding> holdings = holdingService.getActiveHoldings().stream()
                .filter(h -> h.getAsset().getIncludeInNetWorth() == null || h.getAsset().getIncludeInNetWorth())
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
