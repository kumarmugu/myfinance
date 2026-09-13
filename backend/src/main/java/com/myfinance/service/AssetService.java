package com.myfinance.service;

import com.myfinance.config.ReferenceConstraintException;
import com.myfinance.model.Asset;
import com.myfinance.model.Holding;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.AssetRepository;
import com.myfinance.repository.DividendRepository;
import com.myfinance.repository.HoldingRepository;
import com.myfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;
    private final DividendRepository dividendRepository;

    public List<Asset> getAll() { return assetRepository.findAll(); }
    public Asset getById(Long id) { return assetRepository.findById(id).orElseThrow(() -> new RuntimeException("Asset not found: " + id)); }
    public Optional<Asset> getBySymbol(String symbol) { return assetRepository.findBySymbol(symbol); }
    public List<Asset> getByType(AssetType type) { return assetRepository.findByAssetType(type); }
    public List<Asset> search(String query) { return assetRepository.findByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(query, query); }
    public Asset create(Asset asset) {
        // Stamp the price date on creation only if a price was actually provided.
        if (asset.getCurrentPrice() != null) asset.setPriceUpdatedAt(java.time.LocalDateTime.now());
        Asset saved = assetRepository.save(asset);
        log.info("Created Asset id={} symbol={}", saved.getId(), saved.getSymbol());
        return saved;
    }
    public Asset update(Long id, Asset updated) {
        Asset existing = getById(id);
        existing.setName(updated.getName());
        existing.setSymbol(updated.getSymbol());
        existing.setAssetType(updated.getAssetType());
        // Only refresh priceUpdatedAt when the price value genuinely changes, so an unrelated
        // edit (e.g. renaming the asset) doesn't make a stale price look freshly updated.
        applyPriceChange(existing, updated.getCurrentPrice());
        boolean currencyChanged = existing.getCurrency() != updated.getCurrency();
        existing.setCurrency(updated.getCurrency());
        existing.setExchange(updated.getExchange());
        existing.setDescription(updated.getDescription());
        Asset saved = assetRepository.save(existing);
        // The asset's currency is the instrument's source of truth. When it changes, cascade it to
        // every holding of this asset so the Portfolio never shows a holding in a stale currency
        // (the holding stores its own copy, historically seeded from the broker account's default).
        if (currencyChanged && saved.getCurrency() != null) {
            List<Holding> holdings = holdingRepository.findByAssetId(id);
            for (Holding h : holdings) {
                if (h.getCurrency() != saved.getCurrency()) {
                    h.setCurrency(saved.getCurrency());
                    holdingRepository.save(h);
                }
            }
            if (!holdings.isEmpty()) {
                log.info("Cascaded currency {} from Asset id={} to {} holding(s)", saved.getCurrency(), id, holdings.size());
            }
        }
        log.info("Updated Asset id={} symbol={}", id, saved.getSymbol());
        return saved;
    }
    public Asset updatePrice(Long id, BigDecimal price) {
        Asset asset = getById(id);
        applyPriceChange(asset, price);
        return assetRepository.save(asset);
    }

    /** Set the price and stamp priceUpdatedAt only if the value actually differs from the current one. */
    private void applyPriceChange(Asset asset, BigDecimal newPrice) {
        BigDecimal current = asset.getCurrentPrice();
        boolean changed = (current == null)
                ? newPrice != null
                : (newPrice == null || current.compareTo(newPrice) != 0);
        asset.setCurrentPrice(newPrice);
        if (changed && newPrice != null) {
            asset.setPriceUpdatedAt(java.time.LocalDateTime.now());
        }
    }

    public Asset toggleNetWorth(Long id, boolean include) {
        Asset asset = getById(id);
        asset.setIncludeInNetWorth(include);
        return assetRepository.save(asset);
    }

    /** Result of a duplicate-asset merge, so the caller can report what was cleaned up. */
    public record MergeResult(int assetsMerged, int dividendsRepointed, int transactionsRepointed, int holdingsRepointed) {}

    /**
     * Merge duplicate assets created by imports back into the canonical asset. A duplicate is one
     * whose symbol is a descriptive form containing the real ticker in trailing parentheses (e.g.
     * "META PLATFORMS, INC. (META)") when a bare-ticker asset ("META") already exists for the user.
     *
     * <p>References (dividends, transactions, holdings) are repointed to the canonical asset, then
     * the duplicate is deleted. Only same-user assets are touched. Idempotent.
     */
    @org.springframework.transaction.annotation.Transactional
    public MergeResult mergeDuplicateAssets(Long userId) {
        List<Asset> all = assetRepository.findByUserId(userId);
        // Canonical assets keyed by upper-case symbol.
        java.util.Map<String, Asset> bySymbol = new java.util.HashMap<>();
        for (Asset a : all) if (a.getSymbol() != null) bySymbol.putIfAbsent(a.getSymbol().trim().toUpperCase(), a);

        int merged = 0, divs = 0, txns = 0, holds = 0;
        for (Asset dup : all) {
            String ticker = tickerInParens(dup.getSymbol());
            if (ticker == null) continue;                        // not a "NAME (TICKER)" form
            Asset canonical = bySymbol.get(ticker);
            if (canonical == null || canonical.getId().equals(dup.getId())) continue; // no target / itself
            if (!userId.equals(dup.getUserId()) || !userId.equals(canonical.getUserId())) continue;

            for (var d : dividendRepository.findByAssetId(dup.getId())) { d.setAsset(canonical); dividendRepository.save(d); divs++; }
            for (var t : transactionRepository.findByAssetIdOrderByTransactionDateDesc(dup.getId())) { t.setAsset(canonical); transactionRepository.save(t); txns++; }
            for (var h : holdingRepository.findByAssetId(dup.getId())) { h.setAsset(canonical); holdingRepository.save(h); holds++; }
            assetRepository.deleteById(dup.getId());
            merged++;
            log.info("Merged duplicate asset id={} symbol='{}' into canonical id={} ({})",
                    dup.getId(), dup.getSymbol(), canonical.getId(), ticker);
        }
        return new MergeResult(merged, divs, txns, holds);
    }

    /** Return the ticker inside trailing parentheses of a symbol, e.g. "Foo (BAR)" → "BAR", else null. */
    private String tickerInParens(String symbol) {
        if (symbol == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([A-Za-z0-9.\\-]{1,15})\\)\\s*$").matcher(symbol.trim());
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    public void delete(Long id) {
        Asset asset = getById(id);
        List<String> references = new ArrayList<>();

        long txCount = transactionRepository.findByAssetIdOrderByTransactionDateDesc(id).size();
        if (txCount > 0) references.add(txCount + " Transaction(s)");

        long holdingCount = holdingRepository.findByAssetId(id).size();
        if (holdingCount > 0) references.add(holdingCount + " Holding(s)");

        long divCount = dividendRepository.findByAssetId(id).size();
        if (divCount > 0) references.add(divCount + " Dividend(s)");

        if (!references.isEmpty()) {
            log.warn("Cannot delete Asset id={}, referenced by: {}", id, references);
            throw new ReferenceConstraintException("Asset '" + asset.getSymbol() + " - " + asset.getName() + "'", references);
        }

        assetRepository.deleteById(id);
        log.info("Deleted Asset id={}", id);
    }
}
