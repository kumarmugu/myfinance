package com.myfinance.service;

import com.myfinance.model.*;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.*;
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
    private final BankSavingsRepository bankSavingsRepository;
    private final PropertyRepository propertyRepository;
    private final PreciousMetalRepository preciousMetalRepository;
    private final GenericFixedDepositRepository genericFixedDepositRepository;
    private final CurrencyConversionService fx;

    // Standalone module config keys (mirror NetWorthConfigController.MODULE_KEYS)
    public static final String KEY_BANK_SAVINGS = "BANK_SAVINGS";
    public static final String KEY_PROPERTY = "PROPERTY";
    public static final String KEY_PRECIOUS_METAL = "PRECIOUS_METAL";
    public static final String KEY_GENERIC_FD = "GENERIC_FD";

    private Set<String> getIncludedTypesForUser(Long userId) {
        List<NetWorthConfig> configs = netWorthConfigRepository.findByUserIdAndIncludeInNetWorthTrue(userId);
        if (configs.isEmpty()) {
            // No config yet: include everything (all asset types + all modules)
            Set<String> all = Arrays.stream(AssetType.values()).map(Enum::name).collect(Collectors.toSet());
            all.add(KEY_BANK_SAVINGS);
            all.add(KEY_PROPERTY);
            all.add(KEY_PRECIOUS_METAL);
            all.add(KEY_GENERIC_FD);
            return all;
        }
        return configs.stream().map(NetWorthConfig::getAssetType).collect(Collectors.toSet());
    }

    public NetWorthSnapshot takeSnapshot(Long ownerId) {
        return takeSnapshot(ownerId, null);
    }

    public NetWorthSnapshot takeSnapshot(Long ownerId, Long userId) {
        List<Holding> holdings;
        if (ownerId != null) {
            holdings = holdingService.getActiveByOwner(ownerId);
        } else if (userId != null) {
            holdings = holdingService.getActiveByUserId(userId);
        } else {
            holdings = holdingService.getActiveHoldings();
        }

        Set<String> includedTypes = getIncludedTypesForUser(userId);
        holdings = holdings.stream()
                .filter(h -> includedTypes.contains(h.getAsset().getAssetType().name()))
                .collect(Collectors.toList());

        // Holding totals per asset type, converted to base (SGD)
        Map<AssetType, BigDecimal> totals = new EnumMap<>(AssetType.class);
        for (Holding h : holdings) {
            BigDecimal valueBase = fx.toBase(holdingValue(h), currencyCode(h), userId);
            totals.merge(h.getAsset().getAssetType(), valueBase, BigDecimal::add);
        }

        BigDecimal holdingsTotal = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal modulesTotal = moduleTotals(userId, includedTypes).values()
                .stream().reduce(BigDecimal.ZERO, BigDecimal::add);

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

        snapshot.setTotalNetWorth(holdingsTotal.add(modulesTotal));
        return snapshotRepository.save(snapshot);
    }

    public List<NetWorthSnapshot> getHistory() { return snapshotRepository.findAllByOrderBySnapshotDateDesc(); }
    public List<NetWorthSnapshot> getByOwner(Long ownerId) { return snapshotRepository.findByOwnerIdOrderBySnapshotDateDesc(ownerId); }
    public Optional<NetWorthSnapshot> getLatest() { return snapshotRepository.findTopByOrderBySnapshotDateDesc(); }

    /**
     * Current allocation (base currency SGD) for a user: holding asset types plus
     * the standalone module keys, each respecting the net-worth config toggle and
     * the per-record includeInNetWorth flag. This feeds the dashboard total and pie.
     */
    public Map<String, BigDecimal> getCurrentAllocationForUser(Long userId) {
        Set<String> includedTypes = getIncludedTypesForUser(userId);

        Map<String, BigDecimal> allocation = new LinkedHashMap<>();
        holdingService.getActiveByUserId(userId).stream()
                .filter(h -> includedTypes.contains(h.getAsset().getAssetType().name()))
                .forEach(h -> allocation.merge(
                        h.getAsset().getAssetType().name(),
                        fx.toBase(holdingValue(h), currencyCode(h), userId),
                        BigDecimal::add));

        moduleTotals(userId, includedTypes).forEach((k, v) -> {
            if (v.compareTo(BigDecimal.ZERO) != 0) allocation.merge(k, v, BigDecimal::add);
        });
        return allocation;
    }

    /** Total net worth in base currency (SGD) for a user. */
    public BigDecimal getTotalNetWorthForUser(Long userId) {
        return getCurrentAllocationForUser(userId).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Base-currency (SGD) totals for each standalone module, keyed by config key,
     * only when the module is enabled in config. Each item also respects its own
     * includeInNetWorth flag. Properties contribute equity (value - outstanding loan).
     */
    private Map<String, BigDecimal> moduleTotals(Long userId, Set<String> includedTypes) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (userId == null) return result;

        if (includedTypes.contains(KEY_BANK_SAVINGS)) {
            BigDecimal total = bankSavingsRepository.findByUserIdAndIncludeInNetWorthTrue(userId).stream()
                    .map(b -> fx.toBase(b.getBalance(), b.getCurrency() == null ? null : b.getCurrency().name(), userId))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(KEY_BANK_SAVINGS, total);
        }

        if (includedTypes.contains(KEY_PROPERTY)) {
            BigDecimal total = propertyRepository.findByUserIdOrderByPropertyNameAsc(userId).stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIncludeInNetWorth()))
                    .filter(p -> !"SOLD".equals(p.getStatus()))
                    .map(p -> {
                        BigDecimal value = p.getCurrentValue() != null ? p.getCurrentValue() : BigDecimal.ZERO;
                        BigDecimal loan = p.getOutstandingLoan() != null ? p.getOutstandingLoan() : BigDecimal.ZERO;
                        return fx.toBase(value.subtract(loan), p.getCurrency(), userId);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(KEY_PROPERTY, total);
        }

        if (includedTypes.contains(KEY_PRECIOUS_METAL)) {
            BigDecimal total = preciousMetalRepository.findByUserIdAndStatus(userId, "HELD").stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIncludeInNetWorth()))
                    .map(m -> {
                        BigDecimal value = m.getCurrentPrice() != null ? m.getCurrentPrice() : BigDecimal.ZERO;
                        return fx.toBase(value, m.getCurrency(), userId);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(KEY_PRECIOUS_METAL, total);
        }

        if (includedTypes.contains(KEY_GENERIC_FD)) {
            BigDecimal total = genericFixedDepositRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                    .filter(f -> Boolean.TRUE.equals(f.getIncludeInNetWorth()))
                    .map(f -> fx.toBase(f.getPrincipalAmount(), f.getCurrency(), userId))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(KEY_GENERIC_FD, total);
        }

        return result;
    }

    private String currencyCode(Holding h) {
        return h.getCurrency() != null ? h.getCurrency().name() : null;
    }

    private BigDecimal holdingValue(Holding h) {
        BigDecimal price = h.getAsset().getCurrentPrice();
        if (price == null) price = h.getAverageBuyPrice();
        return h.getQuantity().multiply(price);
    }
}
