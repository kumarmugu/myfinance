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

    public NetWorthSnapshot calculateAndSaveSnapshot() {
        return calculateAndSaveSnapshot(LocalDate.now());
    }

    public NetWorthSnapshot calculateAndSaveSnapshot(LocalDate date) {
        List<Holding> activeHoldings = holdingService.getActiveHoldings();

        Map<AssetType, BigDecimal> totals = activeHoldings.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getAsset().getAssetType(),
                        Collectors.reducing(BigDecimal.ZERO,
                                this::calculateHoldingValue,
                                BigDecimal::add)
                ));

        BigDecimal totalEquity = totals.getOrDefault(AssetType.EQUITY, BigDecimal.ZERO);
        BigDecimal totalIndexFund = totals.getOrDefault(AssetType.INDEX_FUND, BigDecimal.ZERO);
        BigDecimal totalMutualFund = totals.getOrDefault(AssetType.MUTUAL_FUND, BigDecimal.ZERO);
        BigDecimal totalCrypto = totals.getOrDefault(AssetType.CRYPTO, BigDecimal.ZERO);
        BigDecimal totalBankDeposit = totals.getOrDefault(AssetType.BANK_DEPOSIT, BigDecimal.ZERO);
        BigDecimal totalNetWorth = totalEquity.add(totalIndexFund).add(totalMutualFund)
                .add(totalCrypto).add(totalBankDeposit);

        // Check if snapshot already exists for this date
        Optional<NetWorthSnapshot> existing = snapshotRepository.findBySnapshotDate(date);
        NetWorthSnapshot snapshot;
        if (existing.isPresent()) {
            snapshot = existing.get();
        } else {
            snapshot = new NetWorthSnapshot();
            snapshot.setSnapshotDate(date);
        }

        snapshot.setTotalEquity(totalEquity);
        snapshot.setTotalIndexFund(totalIndexFund);
        snapshot.setTotalMutualFund(totalMutualFund);
        snapshot.setTotalCrypto(totalCrypto);
        snapshot.setTotalBankDeposit(totalBankDeposit);
        snapshot.setTotalNetWorth(totalNetWorth);

        return snapshotRepository.save(snapshot);
    }

    public List<NetWorthSnapshot> getNetWorthHistory() {
        return snapshotRepository.findAllByOrderBySnapshotDateDesc();
    }

    public List<NetWorthSnapshot> getNetWorthHistoryBetween(LocalDate start, LocalDate end) {
        return snapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDateAsc(start, end);
    }

    public Optional<NetWorthSnapshot> getLatestSnapshot() {
        return snapshotRepository.findTopByOrderBySnapshotDateDesc();
    }

    public Map<String, BigDecimal> getCurrentAllocation() {
        List<Holding> activeHoldings = holdingService.getActiveHoldings();

        Map<AssetType, BigDecimal> totals = activeHoldings.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getAsset().getAssetType(),
                        Collectors.reducing(BigDecimal.ZERO,
                                this::calculateHoldingValue,
                                BigDecimal::add)
                ));

        return totals.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        Map.Entry::getValue
                ));
    }

    private BigDecimal calculateHoldingValue(Holding holding) {
        BigDecimal price = holding.getAsset().getCurrentPrice();
        if (price == null) {
            price = holding.getAverageBuyPrice();
        }
        return holding.getQuantity().multiply(price);
    }
}
