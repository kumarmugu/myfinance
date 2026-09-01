package com.myfinance.controller;

import com.myfinance.model.Asset;
import com.myfinance.model.enums.AssetType;
import com.myfinance.security.TenantContext;
import com.myfinance.service.AssetService;
import com.myfinance.service.PriceFetchService;
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
    private final PriceFetchService priceFetchService;

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

    /**
     * Fetch the latest market price for one asset from the configured online provider and store it.
     * Returns { updated: boolean, asset, message }. If the price cannot be fetched (e.g. Sri Lanka
     * / CSE or an unknown symbol) the existing price is left untouched and updated=false.
     */
    @PostMapping("/{id}/refresh-price")
    public ResponseEntity<?> refreshPrice(@PathVariable Long id) {
        if (!priceFetchService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("error", "Online price lookup is disabled"));
        }
        // Tenant scope: only refresh an asset owned by the current user.
        Long uid = tenantContext.getCurrentUserId();
        Asset asset = assetRepository.findByUserId(uid).stream()
                .filter(a -> a.getId().equals(id)).findFirst().orElse(null);
        if (asset == null) return ResponseEntity.notFound().build();

        var priceOpt = priceFetchService.fetchLatestPrice(asset);
        if (priceOpt.isEmpty()) {
            log.info("No online price for asset id={} symbol={} — leaving current price unchanged", id, asset.getSymbol());
            return ResponseEntity.ok(java.util.Map.of(
                    "updated", false,
                    "asset", asset,
                    "message", "No online price found for " + asset.getSymbol() + " — update it manually"));
        }
        Asset saved = assetService.updatePrice(id, priceOpt.get());
        log.info("Refreshed price for asset id={} symbol={} -> {}", id, asset.getSymbol(), priceOpt.get());
        return ResponseEntity.ok(java.util.Map.of("updated", true, "asset", saved));
    }

    /**
     * Refresh prices for all of the current user's assets. Returns a per-asset summary of how many
     * were updated vs skipped (unfetchable symbols are skipped, prices left unchanged).
     */
    @PostMapping("/refresh-prices")
    public ResponseEntity<?> refreshAllPrices() {
        if (!priceFetchService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("error", "Online price lookup is disabled"));
        }
        Long uid = tenantContext.getCurrentUserId();
        List<Asset> assets = assetRepository.findByUserId(uid);
        int updated = 0;
        java.util.List<String> skipped = new java.util.ArrayList<>();
        for (Asset a : assets) {
            var priceOpt = priceFetchService.fetchLatestPrice(a);
            if (priceOpt.isPresent()) {
                assetService.updatePrice(a.getId(), priceOpt.get());
                updated++;
            } else {
                skipped.add(a.getSymbol());
            }
        }
        log.info("Refresh-all prices for user={}: updated={}, skipped={}", uid, updated, skipped.size());
        return ResponseEntity.ok(java.util.Map.of(
                "updated", updated,
                "skipped", skipped,
                "total", assets.size()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting asset id={}", id);
        assetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
