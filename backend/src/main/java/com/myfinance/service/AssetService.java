package com.myfinance.service;

import com.myfinance.model.Asset;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assetRepository;

    public List<Asset> getAll() { return assetRepository.findAll(); }
    public Asset getById(Long id) { return assetRepository.findById(id).orElseThrow(() -> new RuntimeException("Asset not found: " + id)); }
    public Optional<Asset> getBySymbol(String symbol) { return assetRepository.findBySymbol(symbol); }
    public List<Asset> getByType(AssetType type) { return assetRepository.findByAssetType(type); }
    public List<Asset> search(String query) { return assetRepository.findByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(query, query); }
    public Asset create(Asset asset) { return assetRepository.save(asset); }
    public Asset update(Long id, Asset updated) {
        Asset existing = getById(id);
        existing.setName(updated.getName());
        existing.setSymbol(updated.getSymbol());
        existing.setAssetType(updated.getAssetType());
        existing.setCurrentPrice(updated.getCurrentPrice());
        existing.setCurrency(updated.getCurrency());
        existing.setExchange(updated.getExchange());
        existing.setDescription(updated.getDescription());
        return assetRepository.save(existing);
    }
    public Asset updatePrice(Long id, BigDecimal price) {
        Asset asset = getById(id);
        asset.setCurrentPrice(price);
        return assetRepository.save(asset);
    }
    public void delete(Long id) { assetRepository.deleteById(id); }
}
