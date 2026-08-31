package com.myfinance.service;

import com.myfinance.config.ReferenceConstraintException;
import com.myfinance.model.Asset;
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
        existing.setCurrency(updated.getCurrency());
        existing.setExchange(updated.getExchange());
        existing.setDescription(updated.getDescription());
        Asset saved = assetRepository.save(existing);
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
