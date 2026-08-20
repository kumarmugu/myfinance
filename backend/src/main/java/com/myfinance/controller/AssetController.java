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
    public List<Asset> getAll() { return assetService.getAll(); }

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
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.create(asset));
    }

    @PutMapping("/{id}")
    public Asset update(@PathVariable Long id, @Valid @RequestBody Asset asset) { return assetService.update(id, asset); }

    @PatchMapping("/{id}/price")
    public Asset updatePrice(@PathVariable Long id, @RequestParam BigDecimal price) { return assetService.updatePrice(id, price); }

    @PatchMapping("/{id}/net-worth")
    public Asset toggleNetWorth(@PathVariable Long id, @RequestParam boolean include) {
        return assetService.toggleNetWorth(id, include);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { assetService.delete(id); return ResponseEntity.noContent().build(); }
}
