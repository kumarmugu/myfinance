package com.myfinance.controller;

import com.myfinance.model.Asset;
import com.myfinance.model.enums.AssetType;
import com.myfinance.service.AssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public List<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    @GetMapping("/{id}")
    public Asset getAssetById(@PathVariable Long id) {
        return assetService.getAssetById(id);
    }

    @GetMapping("/type/{type}")
    public List<Asset> getAssetsByType(@PathVariable AssetType type) {
        return assetService.getAssetsByType(type);
    }

    @GetMapping("/search")
    public List<Asset> searchAssets(@RequestParam String query) {
        return assetService.searchAssets(query);
    }

    @PostMapping
    public ResponseEntity<Asset> createAsset(@Valid @RequestBody Asset asset) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createAsset(asset));
    }

    @PutMapping("/{id}")
    public Asset updateAsset(@PathVariable Long id, @Valid @RequestBody Asset asset) {
        return assetService.updateAsset(id, asset);
    }

    @PatchMapping("/{id}/price")
    public Asset updatePrice(@PathVariable Long id, @RequestParam BigDecimal price) {
        return assetService.updateCurrentPrice(id, price);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }
}
