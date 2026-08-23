package com.myfinance.controller;

import com.myfinance.model.Asset;
import com.myfinance.model.enums.AssetType;
import com.myfinance.security.TenantContext;
import com.myfinance.service.AssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Slf4j
public class AssetController {
    private final AssetService assetService;
    private final com.myfinance.repository.AssetRepository assetRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Asset> getAll() { return assetRepository.findByUserId(tenantContext.getCurrentUserId()); }

    @GetMapping("/types")
    public List<String> getAssetTypes() {
        List<String> types = new java.util.ArrayList<>();
        for (AssetType t : AssetType.values()) {
            types.add(t.name());
        }
        return types;
    }

    @GetMapping("/{id}")
    public Asset getById(@PathVariable Long id) { return assetService.getById(id); }

    @GetMapping("/type/{type}")
    public List<Asset> getByType(@PathVariable AssetType type) { return assetService.getByType(type); }

    @GetMapping("/search")
    public List<Asset> search(@RequestParam String query) { return assetService.search(query); }

    @PostMapping
    public ResponseEntity<Asset> create(@Valid @RequestBody Asset asset) {
        log.info("Creating asset: name={}, type={}", asset.getName(), asset.getAssetType());
        asset.setUserId(tenantContext.getCurrentUserId());
        Asset saved = assetService.create(asset);
        log.info("Created asset id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public Asset update(@PathVariable Long id, @Valid @RequestBody Asset asset) {
        log.info("Updating asset id={}", id);
        return assetService.update(id, asset);
    }

    @PatchMapping("/{id}/price")
    public Asset updatePrice(@PathVariable Long id, @RequestParam BigDecimal price) {
        log.info("Updating price for asset id={}, price={}", id, price);
        return assetService.updatePrice(id, price);
    }

    @PatchMapping("/{id}/net-worth")
    public Asset toggleNetWorth(@PathVariable Long id, @RequestParam boolean include) {
        log.info("Toggling net-worth for asset id={}, include={}", id, include);
        return assetService.toggleNetWorth(id, include);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting asset id={}", id);
        assetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
