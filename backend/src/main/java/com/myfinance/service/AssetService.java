package com.myfinance.service;

import com.myfinance.model.Asset;
import com.myfinance.model.enums.AssetType;
import com.myfinance.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset getAssetById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found with id: " + id));
    }

    public List<Asset> getAssetsByType(AssetType type) {
        return assetRepository.findByAssetType(type);
    }

    public List<Asset> searchAssets(String query) {
        return assetRepository.findByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(query, query);
    }

    public Asset createAsset(Asset asset) {
        return assetRepository.save(asset);
    }

    public Asset updateAsset(Long id, Asset updated) {
        Asset existing = getAssetById(id);
        existing.setName(updated.getName());
        existing.setSymbol(updated.getSymbol());
        existing.setAssetType(updated.getAssetType());
        existing.setCurrentPrice(updated.getCurrentPrice());
        existing.setExchange(updated.getExchange());
        existing.setDescription(updated.getDescription());
        return assetRepository.save(existing);
    }

    public Asset updateCurrentPrice(Long id, BigDecimal price) {
        Asset asset = getAssetById(id);
        asset.setCurrentPrice(price);
        return assetRepository.save(asset);
    }

    public void deleteAsset(Long id) {
        assetRepository.deleteById(id);
    }
}
